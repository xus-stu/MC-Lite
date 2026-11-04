import Foundation
import AppKit

enum CLI {

    static func handles(_ argument: String) -> Bool {
        ["--render", "--icon", "--version", "--help", "-h"].contains(argument)
    }

    static func run(_ args: [String]) {
        switch args[1] {
        case "--version":
            print("FlowMini 1.0")
        case "--help", "-h":
            print(usage)
        case "--icon":
            guard args.count > 2 else {
                print("用法：FlowMini --icon <输出目录>")
                return
            }
            do {
                try IconMaker.writeIconset(to: URL(fileURLWithPath: args[2]))
                print("图标已生成：\(args[2])/AppIcon.iconset")
            } catch {
                print("生成图标失败：\(error.localizedDescription)")
            }
        case "--render":
            render(args)
        default:
            print(usage)
        }
    }

    private static var usage: String {
        """
        FlowMini — 纯本地流程图工具

        图形界面：直接打开 App。
        命令行：
          FlowMini --render <输入.flow> [--out 输出] [--theme ink] [--direction LR|TB]
                   [--style orthogonal|curved|straight] [--format png|pdf|svg] [--scale 2]
          FlowMini --icon <输出目录>

        主题：\(Theme.presets.map { $0.id }.joined(separator: " / "))
        """
    }

    private static func render(_ args: [String]) {
        var input: String?
        var output: String?
        var themeID = "ink"
        var direction = FlowDirection.lr
        var style = EdgeStyle.orthogonal
        var format = "png"
        var scale: CGFloat = 2

        var index = 2
        while index < args.count {
            let arg = args[index]
            func next() -> String? {
                guard index + 1 < args.count else { return nil }
                index += 1
                return args[index]
            }
            switch arg {
            case "--out", "-o": output = next()
            case "--theme": if let value = next() { themeID = value }
            case "--direction":
                if let value = next(), let parsed = FlowDirection(rawValue: value.uppercased()) { direction = parsed }
            case "--style":
                if let value = next(), let parsed = EdgeStyle(rawValue: value.lowercased()) { style = parsed }
            case "--format": if let value = next() { format = value.lowercased() }
            case "--scale": if let value = next(), let parsed = Double(value) { scale = CGFloat(parsed) }
            default:
                if input == nil { input = arg }
            }
            index += 1
        }

        guard let path = input else {
            print("缺少输入文件。\n\n\(usage)")
            return
        }
        guard let text = try? String(contentsOfFile: path, encoding: .utf8) else {
            print("读不到文件：\(path)")
            return
        }

        let theme = Theme.named(themeID)
        let parsed = FlowParser.parse(text)
        for problem in parsed.problems { print("提示：\(problem)") }

        let layout = FlowLayout.make(graph: parsed.graph, direction: direction) { node in
            Metrics.nodeSize(node, theme: theme)
        }

        if CommandLine.arguments.contains("--dump") {
            for box in layout.boxes { print("box", box.node.id, box.rect) }
            for route in layout.routes {
                print("edge", route.edge.from, "->", route.edge.to, route.points.map { "(\(Int($0.x)),\(Int($0.y)))" }.joined(separator: " "))
            }
        }

        let target = output ?? defaultOutput(for: path, format: format)
        let url = URL(fileURLWithPath: target)

        do {
            switch format {
            case "pdf":
                try Exporter.writePDF(layout: layout, theme: theme, style: style, to: url)
            case "svg":
                try Exporter.writeSVG(layout: layout, theme: theme, style: style, to: url)
            default:
                try Exporter.writePNG(layout: layout, theme: theme, style: style, scale: scale, to: url)
            }
            print("已生成 \(target)  \(Int(layout.size.width))x\(Int(layout.size.height))  \(parsed.graph.nodes.count) 节点 / \(parsed.graph.edges.count) 连线")
        } catch {
            print("导出失败：\(error.localizedDescription)")
        }
    }

    private static func defaultOutput(for path: String, format: String) -> String {
        let base = (path as NSString).deletingPathExtension
        return base + "." + format
    }
}

// MARK: - 图标

enum IconMaker {

    static func writeIconset(to directory: URL) throws {
        let iconset = directory.appendingPathComponent("AppIcon.iconset")
        try FileManager.default.createDirectory(at: iconset, withIntermediateDirectories: true)

        let entries: [(String, Int)] = [
            ("icon_16x16", 16), ("icon_16x16@2x", 32),
            ("icon_32x32", 32), ("icon_32x32@2x", 64),
            ("icon_128x128", 128), ("icon_128x128@2x", 256),
            ("icon_256x256", 256), ("icon_256x256@2x", 512),
            ("icon_512x512", 512), ("icon_512x512@2x", 1024)
        ]

        for (name, size) in entries {
            let image = try draw(size: CGFloat(size))
            let url = iconset.appendingPathComponent("\(name).png")
            guard let dest = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil) else {
                throw ExportError.writeFailed
            }
            CGImageDestinationAddImage(dest, image, nil)
            guard CGImageDestinationFinalize(dest) else { throw ExportError.writeFailed }
        }
    }

    private static func draw(size: CGFloat) throws -> CGImage {
        let pixels = Int(size)
        guard let ctx = CGContext(data: nil, width: pixels, height: pixels,
                                  bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            throw ExportError.renderFailed
        }

        // 以 1024 为设计尺寸，等比缩放
        let s = size / 1024
        ctx.scaleBy(x: s, y: s)
        ctx.setAllowsAntialiasing(true)

        let bounds = CGRect(x: 56, y: 56, width: 912, height: 912)
        let shape = CGPath(roundedRect: bounds, cornerWidth: 205, cornerHeight: 205, transform: nil)

        ctx.saveGState()
        ctx.addPath(shape)
        ctx.clip()

        let colors = [RGB(0x2B3446).cg, RGB(0x141821).cg] as CFArray
        if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
            ctx.drawLinearGradient(gradient, start: CGPoint(x: 512, y: 968), end: CGPoint(x: 512, y: 56), options: [])
        }

        // 顶部微光
        if let glow = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                 colors: [RGB(0x5B8CFF, 0.55).cg, RGB(0x5B8CFF, 0).cg] as CFArray,
                                 locations: [0, 1]) {
            ctx.drawRadialGradient(glow,
                                   startCenter: CGPoint(x: 300, y: 820), startRadius: 0,
                                   endCenter: CGPoint(x: 300, y: 820), endRadius: 620,
                                   options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
        ctx.restoreGState()

        // 连接线
        let lineColor = RGB(0x2BE8C4)
        ctx.setStrokeColor(lineColor.cg)
        ctx.setLineWidth(26)
        ctx.setLineCap(.round)
        ctx.setLineJoin(.round)

        let mid = CGPoint(x: 500, y: 512)
        let top = CGPoint(x: 640, y: 320)
        let bottom = CGPoint(x: 640, y: 704)

        let path = CGMutablePath()
        path.move(to: CGPoint(x: 388, y: 512))
        path.addLine(to: mid)
        path.addLine(to: CGPoint(x: 500, y: 320))
        path.addLine(to: CGPoint(x: 636, y: 320))
        ctx.addPath(path)
        ctx.strokePath()

        let path2 = CGMutablePath()
        path2.move(to: CGPoint(x: 500, y: 512))
        path2.addLine(to: CGPoint(x: 500, y: 704))
        path2.addLine(to: CGPoint(x: 636, y: 704))
        ctx.addPath(path2)
        ctx.strokePath()

        // 节点
        let nodes: [CGRect] = [
            CGRect(x: 196, y: 442, width: 192, height: 140),
            CGRect(x: 636, y: 250, width: 214, height: 140),
            CGRect(x: 636, y: 634, width: 214, height: 140)
        ]
        for rect in nodes {
            let nodePath = CGPath(roundedRect: rect, cornerWidth: 34, cornerHeight: 34, transform: nil)
            ctx.addPath(nodePath)
            ctx.setFillColor(RGB(0xF7FBFF).cg)
            ctx.fillPath()
        }

        _ = top
        _ = bottom
        _ = Theme.presets.count

        guard let image = ctx.makeImage() else { throw ExportError.renderFailed }
        return image
    }
}
