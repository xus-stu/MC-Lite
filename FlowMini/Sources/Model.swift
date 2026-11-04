import Foundation
import CoreGraphics

// MARK: - 基础类型

enum NodeShape: String, CaseIterable, Codable {
    case box, round, pill, diamond, circle, hexagon, parallelogram, docs

    static func parse(_ raw: String) -> NodeShape? {
        let s = raw.trimmingCharacters(in: .whitespaces).lowercased()
        switch s {
        case "box", "rect", "square", "node", "方框", "矩形", "节点": return .box
        case "round", "rounded", "圆角", "圆角矩形": return .round
        case "pill", "stadium", "capsule", "胶囊": return .pill
        case "diamond", "decision", "rhombus", "菱形", "判断", "条件", "判定": return .diamond
        case "circle", "dot", "ellipse", "圆形", "圆": return .circle
        case "hex", "hexagon", "六边形": return .hexagon
        case "parallelogram", "data", "io", "平行四边形", "数据", "输入输出": return .parallelogram
        case "doc", "docs", "document", "file", "文档", "文件": return .docs
        default: return nil
        }
    }
}

enum FlowDirection: String, CaseIterable, Identifiable, Codable {
    case lr = "LR"
    case tb = "TB"

    var id: String { rawValue }
    var title: String { self == .lr ? "横向" : "纵向" }
    var symbol: String { self == .lr ? "arrow.right" : "arrow.down" }
}

enum EdgeStyle: String, CaseIterable, Identifiable, Codable {
    case orthogonal
    case fork
    case curved
    case straight

    var id: String { rawValue }

    var title: String {
        switch self {
        case .orthogonal: return "直角"
        case .fork: return "分叉"
        case .curved: return "曲线"
        case .straight: return "斜线"
        }
    }

    var hint: String {
        switch self {
        case .orthogonal: return "横平竖直，拐角圆滑"
        case .fork: return "先横后斜，像 a——/b"
        case .curved: return "贝塞尔曲线连接"
        case .straight: return "两点之间一条斜线"
        }
    }
}

// MARK: - 图数据

struct FNode: Identifiable, Equatable {
    var id: String
    var label: String
    var shape: NodeShape
    var order: Int
}

struct FEdge: Identifiable, Equatable {
    var id: Int
    var from: String
    var to: String
    var label: String?
    var dashed: Bool
    var arrow: Bool
}

struct FlowGraph {
    var nodes: [FNode] = []
    var edges: [FEdge] = []
    private var index: [String: Int] = [:]

    mutating func ensure(_ id: String) -> Int {
        if let i = index[id] { return i }
        nodes.append(FNode(id: id, label: id, shape: .box, order: nodes.count))
        index[id] = nodes.count - 1
        return nodes.count - 1
    }

    func nodeID(at index: Int) -> String { nodes[index].id }

    func indexOf(_ id: String) -> Int? { index[id] }
}

// MARK: - 解析

struct ParseResult {
    var graph: FlowGraph
    var problems: [String]
}

enum FlowParser {

    static func parse(_ text: String) -> ParseResult {
        var graph = FlowGraph()
        var problems: [String] = []
        var edgeCounter = 0

        let normalized = text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")

        for (lineIndex, raw) in normalized.components(separatedBy: "\n").enumerated() {
            let lineNo = lineIndex + 1
            let line = raw.trimmed
            if line.isEmpty || line.hasPrefix("#") || line.hasPrefix("//") { continue }

            for rawStatement in line.split(separator: ";", omittingEmptySubsequences: true) {
                let statement = String(rawStatement).trimmed
                if statement.isEmpty { continue }

                if let e = parseEdge(statement) {
                    let fromIdx = graph.ensure(e.from)
                    let toIdx = graph.ensure(e.to)
                    graph.edges.append(
                        FEdge(id: edgeCounter,
                              from: graph.nodes[fromIdx].id,
                              to: graph.nodes[toIdx].id,
                              label: e.label,
                              dashed: e.dashed,
                              arrow: e.arrow)
                    )
                    edgeCounter += 1
                    continue
                }

                if let n = parseNode(statement, problems: &problems, lineNo: lineNo) {
                    let i = graph.ensure(n.id)
                    graph.nodes[i].label = n.label
                    graph.nodes[i].shape = n.shape
                    continue
                }

                problems.append("第 \(lineNo) 行看不懂：\(statement)")
            }
        }

        return ParseResult(graph: graph, problems: Array(problems.prefix(4)))
    }

    // "a -> b", "a --> b: 否", "a -- b"
    private static func parseEdge(_ raw: String) -> (from: String, to: String, label: String?, dashed: Bool, arrow: Bool)? {
        let s = raw
            .replacingOccurrences(of: "→", with: "->")
            .replacingOccurrences(of: "-->", with: "@@DASH@@")

        var separator = "->"
        var dashed = false
        var arrow = true

        if s.contains("@@DASH@@") {
            separator = "@@DASH@@"
            dashed = true
        } else if s.contains("->") {
            separator = "->"
        } else if s.contains("--") {
            separator = "--"
            arrow = false
        } else {
            return nil
        }

        guard let range = s.range(of: separator) else { return nil }
        var lhs = String(s[..<range.lowerBound]).trimmed
        var rhs = String(s[range.upperBound...]).trimmed

        var label: String?
        if let colon = rhs.firstIndex(of: ":") {
            label = String(rhs[rhs.index(after: colon)...]).trimmed
            rhs = String(rhs[..<colon]).trimmed
        }

        // 允许在两端写形状，例如 a[round] -> b
        if let bracket = lhs.firstIndex(of: "[") { lhs = String(lhs[..<bracket]).trimmed }
        if let bracket = rhs.firstIndex(of: "[") { rhs = String(rhs[..<bracket]).trimmed }
        lhs = lhs.replacingOccurrences(of: "@@DASH@@", with: "")
        rhs = rhs.replacingOccurrences(of: "@@DASH@@", with: "")

        guard !lhs.isEmpty, !rhs.isEmpty else { return nil }
        guard !lhs.contains(where: { $0.isWhitespace }), !rhs.contains(where: { $0.isWhitespace }) else { return nil }

        if let l = label {
            label = l.isEmpty ? nil : l.replacingOccurrences(of: "\\n", with: "\n")
        }
        return (lhs, rhs, label, dashed, arrow)
    }

    // "a", "a: 标签", "a[diamond]: 判断？"
    private static func parseNode(_ raw: String, problems: inout [String], lineNo: Int) -> (id: String, label: String, shape: NodeShape)? {
        var head = raw
        var label: String?

        if let colon = raw.firstIndex(of: ":") {
            head = String(raw[..<colon]).trimmed
            label = String(raw[raw.index(after: colon)...]).trimmed
        }

        var shape: NodeShape = .box
        if let open = head.firstIndex(of: "["),
           let close = head.firstIndex(of: "]"),
           open < close {
            let shapeText = String(head[head.index(after: open)..<close]).trimmed
            if let parsed = NodeShape.parse(shapeText) {
                shape = parsed
            } else {
                problems.append("第 \(lineNo) 行未知形状「\(shapeText)」，已用方框")
            }
            head = String(head[..<open]).trimmed
        }

        guard !head.isEmpty, !head.contains(where: { $0.isWhitespace }) else { return nil }

        let text = (label?.isEmpty ?? true) ? head : label!
        return (head, text.replacingOccurrences(of: "\\n", with: "\n"), shape)
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespaces) }
}

// MARK: - 示例

enum Samples {
    static let welcome = """
    # 左边改文字，右边立刻出图；所有内容只保存在本机。
    # 连线    a -> b        加标签   a -> b: 是
    # 形状    a[round]: 开始   (box / round / pill / diamond / circle / hex)

    start[round]: 开始
    collect: 收集需求
    review[diamond]: 需要评审？
    doc: 写设计文档
    code: 直接编码
    done[round]: 交付上线

    start -> collect
    collect -> review
    review -> doc: 需要
    review -> code: 不需要
    doc -> done
    code -> done
    """

    static let minimal = """
    a -> b
    a -> c
    """

    static let tree = """
    root[round]: 主页
    a: 产品
    b: 方案
    c: 价格
    a1: 功能
    a2: 案例
    b1: 标准版
    b2: 旗舰版

    root -> a
    root -> b
    root -> c
    a -> a1
    a -> a2
    b -> b1
    b -> b2
    """

    static let loop = """
    begin[round]: 收到请求
    check[diamond]: 参数合法？
    fix: 返回错误
    work: 处理业务
    save[hex]: 写入数据库
    back[diamond]: 需要重试？
    end[round]: 响应

    begin -> check
    check -> fix: 否
    check -> work: 是
    work -> save
    save -> back
    back -> work: 是
    back -> end: 否
    fix -> end
    """

    static let state = """
    idle[circle]: 空闲
    load[circle]: 加载中
    ready[circle]: 就绪
    error[circle]: 出错

    idle -> load: 点击
    load -> ready: 成功
    load -> error: 失败
    error -> load: 重试
    ready -> idle: 重置
    """

    static let all: [(title: String, text: String)] = [
        ("入门示例", welcome),
        ("最小分支 a / b / c", minimal),
        ("树形结构", tree),
        ("带循环的流程", loop),
        ("状态机", state)
    ]

    static let starter = """
    # 在这里输入，例如：
    a -> b
    a -> c
    """
}
