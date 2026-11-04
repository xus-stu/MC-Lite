import Foundation
import CoreGraphics

// MARK: - 结果类型

struct LayoutOptions {
    var layerGap: CGFloat = 78
    var nodeGap: CGFloat = 26
    var margin: CGFloat = 46
}

enum Density: String, CaseIterable, Identifiable, Codable {
    case compact, normal, loose

    var id: String { rawValue }
    var title: String {
        switch self {
        case .compact: return "紧凑"
        case .normal: return "标准"
        case .loose: return "宽松"
        }
    }

    var options: LayoutOptions {
        switch self {
        case .compact: return LayoutOptions(layerGap: 58, nodeGap: 18, margin: 36)
        case .normal: return LayoutOptions(layerGap: 78, nodeGap: 26, margin: 46)
        case .loose: return LayoutOptions(layerGap: 106, nodeGap: 40, margin: 60)
        }
    }
}

struct NodeBox {
    var node: FNode
    var rect: CGRect
}

struct EdgeRoute {
    var edge: FEdge
    var points: [CGPoint] = []
    var labelPoint: CGPoint?
    var arrowAngle: CGFloat = 0
    var isBack: Bool = false
    var isLoop: Bool = false
}

struct LayoutResult {
    var boxes: [NodeBox] = []
    var routes: [EdgeRoute] = []
    var size: CGSize = .zero
    var horizontal: Bool = true

    func rect(for id: String) -> CGRect? {
        boxes.first { $0.node.id == id }?.rect
    }
}

// MARK: - 布局

enum FlowLayout {

    private struct LNode {
        var key: String
        var node: FNode?
        var size: CGSize
        var layer: Int
    }

    static func make(graph: FlowGraph,
                     direction: FlowDirection,
                     options: LayoutOptions = LayoutOptions(),
                     sizeFor: (FNode) -> CGSize) -> LayoutResult {

        guard !graph.nodes.isEmpty else { return LayoutResult() }
        let horizontal = direction == .lr

        // ---- 邻接表（原始图） ----
        var outAdj: [String: [String]] = [:]
        var inAdj: [String: [String]] = [:]
        for e in graph.edges where e.from != e.to {
            outAdj[e.from, default: []].append(e.to)
            inAdj[e.to, default: []].append(e.from)
        }
        for key in outAdj.keys { outAdj[key] = dedupe(outAdj[key]!) }
        for key in inAdj.keys { inAdj[key] = dedupe(inAdj[key]!) }

        // ---- 找回边（DFS） ----
        var color: [String: Int] = [:]
        var backEdges = Set<String>()
        func visit(_ u: String) {
            color[u] = 1
            for v in outAdj[u] ?? [] {
                let c = color[v] ?? 0
                if c == 1 {
                    backEdges.insert(edgeKey(u, v))
                } else if c == 0 {
                    visit(v)
                }
            }
            color[u] = 2
        }
        for n in graph.nodes where color[n.id] == nil { visit(n.id) }

        // ---- 分层（最长路径） ----
        var rank: [String: Int] = [:]
        for n in graph.nodes { rank[n.id] = 0 }
        var indeg: [String: Int] = [:]
        for (u, list) in outAdj {
            for v in list where !backEdges.contains(edgeKey(u, v)) {
                indeg[v, default: 0] += 1
            }
        }
        var queue: [String] = graph.nodes.filter { (indeg[$0.id] ?? 0) == 0 }.map { $0.id }
        var head = 0
        while head < queue.count {
            let u = queue[head]
            head += 1
            for v in outAdj[u] ?? [] where !backEdges.contains(edgeKey(u, v)) {
                rank[v] = max(rank[v] ?? 0, (rank[u] ?? 0) + 1)
                indeg[v, default: 0] -= 1
                if indeg[v] == 0 { queue.append(v) }
            }
        }

        // 归一化
        if let minRank = rank.values.min(), minRank > 0 {
            for (k, v) in rank { rank[k] = v - minRank }
        }
        let layerCount = (rank.values.max() ?? 0) + 1

        // ---- 虚拟节点（跨层连线占位） ----
        var lnodes: [LNode] = graph.nodes.map {
            LNode(key: $0.id, node: $0, size: sizeFor($0), layer: rank[$0.id] ?? 0)
        }
        var chains: [Int: [String]] = [:]
        for e in graph.edges {
            let rf = rank[e.from] ?? 0
            let rt = rank[e.to] ?? 0
            var keys: [String] = [e.from]
            if e.from != e.to, !backEdges.contains(edgeKey(e.from, e.to)), rt - rf > 1 {
                for layer in (rf + 1)..<rt {
                    let key = "~\(e.id)~\(layer)"
                    lnodes.append(LNode(key: key, node: nil, size: .zero, layer: layer))
                    keys.append(key)
                }
            }
            keys.append(e.to)
            chains[e.id] = keys
        }

        var byKey: [String: LNode] = [:]
        for n in lnodes { byKey[n.key] = n }
        var layerOf: [String: Int] = [:]
        for n in lnodes { layerOf[n.key] = n.layer }

        // ---- 分层排序用邻接（只连接相邻层） ----
        var kOut: [String: [String]] = [:]
        var kIn: [String: [String]] = [:]
        for e in graph.edges {
            guard let keys = chains[e.id] else { continue }
            if e.from == e.to { continue }
            if backEdges.contains(edgeKey(e.from, e.to)) { continue }
            for i in 0..<(keys.count - 1) {
                kOut[keys[i], default: []].append(keys[i + 1])
                kIn[keys[i + 1], default: []].append(keys[i])
            }
        }

        // ---- 每层节点 ----
        var layers = [[String]](repeating: [], count: layerCount)
        for n in lnodes {
            let l = min(max(n.layer, 0), layerCount - 1)
            layers[l].append(n.key)
        }

        // ---- 初始顺序 + 重心法减少交叉 ----
        var order: [String: Double] = [:]
        for layer in layers {
            for (i, key) in layer.enumerated() { order[key] = Double(i) }
        }

        func sortLayer(_ index: Int, using adj: [String: [String]]) {
            let keys = layers[index]
            guard keys.count > 1 else { return }
            var bary: [String: Double] = [:]
            for key in keys {
                let neighbors = adj[key] ?? []
                if neighbors.isEmpty {
                    bary[key] = order[key] ?? 0
                } else {
                    var sum = 0.0
                    var count = 0.0
                    for nb in neighbors {
                        if let o = order[nb] { sum += o; count += 1 }
                    }
                    bary[key] = count > 0 ? sum / count : (order[key] ?? 0)
                }
            }
            layers[index].sort { a, b in
                let ba = bary[a] ?? 0, bb = bary[b] ?? 0
                if abs(ba - bb) > 0.0001 { return ba < bb }
                return (order[a] ?? 0) < (order[b] ?? 0)
            }
            for (i, key) in layers[index].enumerated() { order[key] = Double(i) }
        }

        if layerCount > 1 {
            for _ in 0..<4 {
                if layerCount > 2 {
                    for l in 1..<layerCount { sortLayer(l, using: kIn) }
                }
                for l in stride(from: layerCount - 2, through: 0, by: -1) {
                    guard l >= 0 else { break }
                    sortLayer(l, using: kOut)
                }
            }
        }

        // ---- 列宽 ----
        func rankExtent(_ s: CGSize) -> CGFloat { horizontal ? s.width : s.height }
        func crossExtent(_ s: CGSize) -> CGFloat { horizontal ? s.height : s.width }

        var colStart = [CGFloat](repeating: 0, count: layerCount)
        var colThickness = [CGFloat](repeating: 0, count: layerCount)
        var cursor: CGFloat = 0
        for l in 0..<layerCount {
            colStart[l] = cursor
            let t = layers[l].map { rankExtent(byKey[$0]!.size) }.max() ?? 0
            colThickness[l] = t
            cursor += t + options.layerGap
        }

        // ---- 初始纵向位置 ----
        var cross: [String: CGFloat] = [:]
        for l in 0..<layerCount {
            let keys = layers[l]
            let total = keys.map { crossExtent(byKey[$0]!.size) }.reduce(0, +)
                + options.nodeGap * CGFloat(max(0, keys.count - 1))
            var c = -total / 2
            for key in keys {
                let e = crossExtent(byKey[key]!.size)
                cross[key] = c + e / 2
                c += e + options.nodeGap
            }
        }

        // ---- 松弛：让父节点尽量对齐子节点 ----
        if layerCount > 1 {
            for iteration in 0..<160 {
                var maxDelta: CGFloat = 0
                let ascending = iteration % 2 == 0
                let order2: [Int] = ascending
                    ? Array(1..<layerCount)
                    : Array(stride(from: layerCount - 2, through: 0, by: -1))

                for l in order2 {
                    let keys = layers[l]
                    var targets: [String: CGFloat] = [:]

                    for key in keys {
                        let isDummy = byKey[key]?.node == nil
                        var neighbors = (kIn[key] ?? []) + (kOut[key] ?? [])
                        if !isDummy {
                            // 真实节点不受虚拟节点拉扯，避免主链被带弯
                            neighbors = neighbors.filter { byKey[$0]?.node != nil }
                        }
                        if neighbors.isEmpty { continue }
                        var sum: CGFloat = 0
                        for nb in neighbors { sum += cross[nb] ?? 0 }
                        let target = sum / CGFloat(neighbors.count)
                        targets[key] = target
                        let delta = target - (cross[key] ?? 0)
                        cross[key]! += delta * 0.55
                        maxDelta = max(maxDelta, abs(delta))
                    }

                    guard keys.count > 1 else { continue }

                    // 1) 硬约束：同层节点必须留出间距（按顺序推进，避免互相穿插）
                    for i in 1..<keys.count {
                        let previous = keys[i - 1]
                        let current = keys[i]
                        let minGap = (crossExtent(byKey[previous]!.size) + crossExtent(byKey[current]!.size)) / 2 + options.nodeGap
                        let minimum = (cross[previous] ?? 0) + minGap
                        if (cross[current] ?? 0) < minimum {
                            cross[current] = minimum
                        }
                    }

                    // 2) 整体回正到期望中心，避免单向挤压越来越偏
                    if !targets.isEmpty {
                        var desired: CGFloat = 0
                        for key in keys { desired += targets[key] ?? (cross[key] ?? 0) }
                        desired /= CGFloat(keys.count)
                        var current: CGFloat = 0
                        for key in keys { current += cross[key] ?? 0 }
                        current /= CGFloat(keys.count)
                        let shift = desired - current
                        if abs(shift) > 0.05 {
                            for key in keys { cross[key]! += shift }
                        }
                    }
                }
                if maxDelta < 0.2 { break }
            }

            // 收尾：再压一遍间距，确保任何情况下都不重叠
            for l in 0..<layerCount where layers[l].count > 1 {
                let keys = layers[l]
                for i in 1..<keys.count {
                    let previous = keys[i - 1]
                    let current = keys[i]
                    let minGap = (crossExtent(byKey[previous]!.size) + crossExtent(byKey[current]!.size)) / 2 + options.nodeGap
                    let minimum = (cross[previous] ?? 0) + minGap
                    if (cross[current] ?? 0) < minimum {
                        cross[current] = minimum
                    }
                }
            }
        }

        // ---- 生成矩形 ----
        var boxes: [NodeBox] = []
        for l in 0..<layerCount {
            let thickness = colThickness[l]
            for key in layers[l] {
                guard let node = byKey[key]?.node else { continue }
                let s = byKey[key]!.size
                let offset = (thickness - rankExtent(s)) / 2
                let rp = colStart[l] + offset
                let c = cross[key] ?? 0
                let rect = horizontal
                    ? CGRect(x: rp, y: c - s.height / 2, width: s.width, height: s.height)
                    : CGRect(x: c - s.width / 2, y: rp, width: s.width, height: s.height)
                boxes.append(NodeBox(node: node, rect: rect))
            }
        }

        // ---- 平移到画布坐标 ----
        var minX = boxes.map { $0.rect.minX }.min() ?? 0
        var minY = boxes.map { $0.rect.minY }.min() ?? 0
        minX -= options.margin
        minY -= options.margin
        for i in boxes.indices {
            boxes[i].rect.origin.x -= minX
            boxes[i].rect.origin.y -= minY
        }
        for l in 0..<layerCount { colStart[l] -= (horizontal ? minX : minY) }
        let crossShift = horizontal ? -minY : -minX
        if crossShift != 0 {
            for key in cross.keys { cross[key]! += crossShift }
        }

        var rects: [String: CGRect] = [:]
        for b in boxes { rects[b.node.id] = b.rect }

        func gapCenter(_ layer: Int) -> CGFloat {
            guard layer > 0 else { return colStart[0] - options.layerGap / 2 }
            return (colStart[layer - 1] + colThickness[layer - 1] + colStart[layer]) / 2
        }

        func point(_ rankCoord: CGFloat, _ crossCoord: CGFloat) -> CGPoint {
            horizontal ? CGPoint(x: rankCoord, y: crossCoord) : CGPoint(x: crossCoord, y: rankCoord)
        }

        func crossOf(_ p: CGPoint) -> CGFloat { horizontal ? p.y : p.x }

        // ---- 连线 ----
        var routes: [EdgeRoute] = []
        var backIndex = 0

        for e in graph.edges {
            let isBack = e.from != e.to && backEdges.contains(edgeKey(e.from, e.to))
            let isLoop = e.from == e.to

            if isLoop, let r = rects[e.from] {
                var route = EdgeRoute(edge: e, isBack: false, isLoop: true)
                if horizontal {
                    route.points = [CGPoint(x: r.maxX - 4, y: r.minY + 10),
                                    CGPoint(x: r.maxX + 34, y: r.minY + 10),
                                    CGPoint(x: r.maxX + 34, y: r.maxY + 8),
                                    CGPoint(x: r.minX + r.width * 0.55, y: r.maxY + 8),
                                    CGPoint(x: r.minX + r.width * 0.55, y: r.maxY - 2)]
                } else {
                    route.points = [CGPoint(x: r.maxX - 10, y: r.maxY - 4),
                                    CGPoint(x: r.maxX - 10, y: r.maxY + 30),
                                    CGPoint(x: r.maxX + 8, y: r.maxY + 30),
                                    CGPoint(x: r.maxX + 8, y: r.minY + r.height * 0.55)]
                }
                route.arrowAngle = arrowAngle(route.points)
                route.labelPoint = pointAlong(route.points, 0.72)
                routes.append(route)
                continue
            }

            guard let a = rects[e.from], let b = rects[e.to] else { continue }

            if isBack {
                var route = EdgeRoute(edge: e, isBack: true)
                let depth = 46 + CGFloat(backIndex % 3) * 18
                backIndex += 1
                if horizontal {
                    let p0 = CGPoint(x: a.midX, y: a.maxY)
                    let p1 = CGPoint(x: b.midX, y: b.maxY)
                    let y = max(a.maxY, b.maxY) + depth
                    route.points = [p0, CGPoint(x: p0.x, y: y), CGPoint(x: p1.x, y: y), p1]
                } else {
                    let p0 = CGPoint(x: a.maxX, y: a.midY)
                    let p1 = CGPoint(x: b.maxX, y: b.midY)
                    let x = max(a.maxX, b.maxX) + depth
                    route.points = [p0, CGPoint(x: x, y: p0.y), CGPoint(x: x, y: p1.y), p1]
                }
                route.arrowAngle = arrowAngle(route.points)
                route.labelPoint = pointAlong(route.points, 0.72)
                routes.append(route)
                continue
            }

            var route = EdgeRoute(edge: e)
            let p0 = outPort(a, horizontal: horizontal)
            let p1 = inPort(b, horizontal: horizontal)
            let keys = chains[e.id] ?? [e.from, e.to]

            if keys.count > 2 {
                var pts: [CGPoint] = [p0]
                for key in keys.dropFirst().dropLast() {
                    guard let layer = layerOf[key], let c = cross[key] else { continue }
                    let g = gapCenter(layer)
                    pts.append(point(g, crossOf(pts.last!)))
                    pts.append(point(g, c))
                }
                if let targetLayer = layerOf[e.to] {
                    let g = gapCenter(targetLayer)
                    pts.append(point(g, crossOf(pts.last!)))
                    pts.append(point(g, crossOf(p1)))
                }
                pts.append(p1)
                route.points = simplify(pts)
            } else {
                let targetLayer = layerOf[e.to] ?? 0
                let g = gapCenter(targetLayer)
                if abs(crossOf(p0) - crossOf(p1)) < 3 {
                    route.points = [p0, p1]
                } else {
                    route.points = simplify([p0,
                                             point(g, crossOf(p0)),
                                             point(g, crossOf(p1)),
                                             p1])
                }
            }

            route.arrowAngle = arrowAngle(route.points)
            route.labelPoint = pointAlong(route.points, 0.72)
            routes.append(route)
        }

        // ---- 画布尺寸 ----
        var maxX: CGFloat = 0
        var maxY: CGFloat = 0
        for b in boxes {
            maxX = max(maxX, b.rect.maxX)
            maxY = max(maxY, b.rect.maxY)
        }
        for r in routes {
            for p in r.points {
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
        }
        let size = CGSize(width: maxX + options.margin, height: maxY + options.margin)

        return LayoutResult(boxes: boxes, routes: routes, size: size, horizontal: horizontal)
    }

    // MARK: - 辅助

    private static func dedupe(_ list: [String]) -> [String] {
        var seen = Set<String>()
        return list.filter { seen.insert($0).inserted }
    }

    private static func edgeKey(_ a: String, _ b: String) -> String { "\(a)\u{1F}\(b)" }

    private static func outPort(_ r: CGRect, horizontal: Bool) -> CGPoint {
        horizontal ? CGPoint(x: r.maxX, y: r.midY) : CGPoint(x: r.midX, y: r.maxY)
    }

    private static func inPort(_ r: CGRect, horizontal: Bool) -> CGPoint {
        horizontal ? CGPoint(x: r.minX, y: r.midY) : CGPoint(x: r.midX, y: r.minY)
    }

    private static func arrowAngle(_ points: [CGPoint]) -> CGFloat {
        guard points.count >= 2 else { return 0 }
        let p1 = points[points.count - 1]
        let p0 = points[points.count - 2]
        return atan2(p1.y - p0.y, p1.x - p0.x)
    }

    private static func pointAlong(_ points: [CGPoint], _ fraction: CGFloat) -> CGPoint? {
        guard let first = points.first else { return nil }
        guard points.count > 1 else { return first }
        var total: CGFloat = 0
        for i in 1..<points.count {
            total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        guard total > 0 else { return first }
        var target = total * fraction
        for i in 1..<points.count {
            let d = hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            if target <= d || i == points.count - 1 {
                let t = d > 0 ? target / d : 0
                return CGPoint(x: points[i - 1].x + (points[i].x - points[i - 1].x) * t,
                               y: points[i - 1].y + (points[i].y - points[i - 1].y) * t)
            }
            target -= d
        }
        return points.last
    }

    private static func simplify(_ points: [CGPoint]) -> [CGPoint] {
        var result: [CGPoint] = []
        for p in points {
            if let last = result.last, abs(last.x - p.x) < 0.01, abs(last.y - p.y) < 0.01 { continue }
            result.append(p)
        }
        guard result.count > 2 else { return result }
        var cleaned: [CGPoint] = [result[0]]
        for i in 1..<(result.count - 1) {
            let a = cleaned.last!
            let b = result[i]
            let c = result[i + 1]
            let cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
            if abs(cross) > 0.01 { cleaned.append(b) }
        }
        cleaned.append(result.last!)
        return cleaned
    }
}
