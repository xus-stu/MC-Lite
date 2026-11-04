import Foundation
import AppKit
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

enum ExportError: LocalizedError {
    case renderFailed
    case writeFailed

    var errorDescription: String? {
        switch self {
        case .renderFailed: return "无法创建图像"
        case .writeFailed: return "写入文件失败"
        }
    }
}

enum Exporter {

    static func image(layout: LayoutResult, theme: Theme, style: EdgeStyle, scale: CGFloat) throws -> CGImage {
        let pixelWidth = Int(ceil(layout.size.width * scale))
        let pixelHeight = Int(ceil(layout.size.height * scale))
        guard pixelWidth > 1, pixelHeight > 1,
              let ctx = CGContext(data: nil,
                                  width: pixelWidth,
                                  height: pixelHeight,
                                  bitsPerComponent: 8,
                                  bytesPerRow: 0,
                                  space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            throw ExportError.renderFailed
        }

        ctx.scaleBy(x: scale, y: scale)
        ctx.translateBy(x: 0, y: layout.size.height)
        ctx.scaleBy(x: 1, y: -1)

        let renderer = DiagramRenderer(layout: layout, theme: theme, style: style)
        renderer.draw(in: ctx, canvas: layout.size)

        guard let image = ctx.makeImage() else { throw ExportError.renderFailed }
        return image
    }

    static func writePNG(layout: LayoutResult, theme: Theme, style: EdgeStyle, scale: CGFloat, to url: URL) throws {
        let image = try image(layout: layout, theme: theme, style: style, scale: scale)
        guard let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
            throw ExportError.writeFailed
        }
        CGImageDestinationAddImage(dest, image, nil)
        guard CGImageDestinationFinalize(dest) else { throw ExportError.writeFailed }
    }

    static func writePDF(layout: LayoutResult, theme: Theme, style: EdgeStyle, to url: URL) throws {
        var box = CGRect(origin: .zero, size: layout.size)
        guard let consumer = CGDataConsumer(url: url as CFURL),
              let ctx = CGContext(consumer: consumer, mediaBox: &box, nil) else {
            throw ExportError.writeFailed
        }
        ctx.beginPDFPage(nil)
        ctx.translateBy(x: 0, y: layout.size.height)
        ctx.scaleBy(x: 1, y: -1)
        let renderer = DiagramRenderer(layout: layout, theme: theme, style: style)
        renderer.draw(in: ctx, canvas: layout.size)
        ctx.endPDFPage()
        ctx.closePDF()
    }

    static func writeSVG(layout: LayoutResult, theme: Theme, style: EdgeStyle, to url: URL) throws {
        let text = SVGWriter.make(layout: layout, theme: theme, style: style)
        try text.write(to: url, atomically: true, encoding: .utf8)
    }
}

// MARK: - SVG

enum SVGWriter {

    static func make(layout: LayoutResult, theme: Theme, style: EdgeStyle) -> String {
        var out = ""
        let w = fmt(layout.size.width)
        let h = fmt(layout.size.height)

        out += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        out += "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"\(w)\" height=\"\(h)\" viewBox=\"0 0 \(w) \(h)\">\n"
        out += "<rect width=\"100%\" height=\"100%\" fill=\"\(hex(theme.bg))\"/>\n"

        if theme.gridStyle == .dots {
            out += "<defs><pattern id=\"dots\" width=\"26\" height=\"26\" patternUnits=\"userSpaceOnUse\">"
            out += "<circle cx=\"1\" cy=\"1\" r=\"0.9\" fill=\"\(hex(theme.grid))\"/></pattern></defs>"
            out += "<rect width=\"100%\" height=\"100%\" fill=\"url(#dots)\"/>\n"
        } else if theme.gridStyle == .lines {
            out += "<defs><pattern id=\"grid\" width=\"26\" height=\"26\" patternUnits=\"userSpaceOnUse\">"
            out += "<path d=\"M 26 0 L 0 0 0 26\" fill=\"none\" stroke=\"\(hex(theme.grid))\" stroke-width=\"0.6\"/>"
            out += "</pattern></defs><rect width=\"100%\" height=\"100%\" fill=\"url(#grid)\"/>\n"
        }

        // 连线
        for route in layout.routes {
            guard route.points.count >= 2 else { continue }
            let d = pathData(for: route, style: style, horizontal: layout.horizontal)
            out += "<path d=\"\(d)\" fill=\"none\" stroke=\"\(hex(theme.edge))\" stroke-width=\"\(fmt(theme.lineWidth * 0.95))\""
            out += " stroke-linecap=\"round\" stroke-linejoin=\"round\""
            if route.edge.dashed { out += " stroke-dasharray=\"6 4.5\"" }
            out += "/>\n"

            if route.edge.arrow {
                let angle = arrowAngle(route: route, style: style, horizontal: layout.horizontal)
                let end = route.points.last!
                let length: CGFloat = 9, half: CGFloat = 4.6
                let tip = CGPoint(x: end.x - cos(angle) * 1.2, y: end.y - sin(angle) * 1.2)
                let p1 = CGPoint(x: tip.x - cos(angle) * length + cos(angle + .pi / 2) * half,
                                 y: tip.y - sin(angle) * length + sin(angle + .pi / 2) * half)
                let p2 = CGPoint(x: tip.x - cos(angle) * length * 0.72,
                                 y: tip.y - sin(angle) * length * 0.72)
                let p3 = CGPoint(x: tip.x - cos(angle) * length - cos(angle + .pi / 2) * half,
                                 y: tip.y - sin(angle) * length - sin(angle + .pi / 2) * half)
                out += "<path d=\"M \(fmt(tip.x)) \(fmt(tip.y)) L \(fmt(p1.x)) \(fmt(p1.y)) L \(fmt(p2.x)) \(fmt(p2.y)) L \(fmt(p3.x)) \(fmt(p3.y)) Z\" fill=\"\(hex(theme.edge))\"/>\n"
            }
        }

        // 节点
        for (index, box) in layout.boxes.enumerated() {
            let path = box.node.shape == .docs
                ? NodePathBuilder.docsPath(rect: box.rect, corner: theme.corner)
                : NodePathBuilder.path(shape: box.node.shape, rect: box.rect, corner: theme.corner)
            let fill = theme.cardPalette.isEmpty ? theme.card : theme.cardPalette[index % theme.cardPalette.count]
            let stroke = hex(theme.stroke)
            out += "<path d=\"\(pathData(path))\" fill=\"\(hex(fill))\" stroke=\"\(stroke)\" stroke-width=\"\(fmt(theme.lineWidth))\"/>\n"

            let font = theme.font()
            let luminance = 0.299 * fill.r + 0.587 * fill.g + 0.114 * fill.b
            let textColor = (luminance < 0.45 && !theme.isDark) ? RGB(0xFFFFFF, 0.94) : theme.text
            let inset = box.rect.insetBy(dx: 10, dy: 8)
            out += textElements(box.node.label, in: inset, font: font, color: textColor)
        }

        // 连线文字
        let labelFont = theme.font(size: max(10.5, theme.fontSize - 2.2))
        for route in layout.routes {
            guard let label = route.edge.label, !label.isEmpty, let center = route.labelPoint else { continue }
            let size = TextDraw.measure(label, font: labelFont, lineHeight: Metrics.lineHeight(labelFont))
            let chip = CGRect(x: center.x - size.width / 2 - 7,
                              y: center.y - size.height / 2 - 3,
                              width: size.width + 14,
                              height: size.height + 6)
            out += "<rect x=\"\(fmt(chip.minX))\" y=\"\(fmt(chip.minY))\" width=\"\(fmt(chip.width))\" height=\"\(fmt(chip.height))\" rx=\"5\" fill=\"\(hex(theme.labelBackground))\" fill-opacity=\"0.95\"/>\n"
            out += textElements(label, in: chip.insetBy(dx: 7, dy: 3), font: labelFont, color: theme.text.alpha(0.86))
        }

        out += "</svg>\n"
        return out
    }

    // MARK: 辅助

    private static func textElements(_ text: String, in rect: CGRect, font: NSFont, color: RGB) -> String {
        let lines = text.isEmpty ? [""] : text.components(separatedBy: "\n")
        let lineHeight = Metrics.lineHeight(font)
        let total = CGFloat(lines.count) * lineHeight
        let top = rect.midY - total / 2
        let family = font.familyName ?? "PingFang SC"
        let ascender = font.ascender
        let descender = font.descender

        var out = ""
        for (index, line) in lines.enumerated() {
            let centerY = top + lineHeight * CGFloat(index) + lineHeight / 2
            let baseline = centerY + (ascender + descender) / 2
            out += "<text x=\"\(fmt(rect.midX))\" y=\"\(fmt(baseline))\" text-anchor=\"middle\" "
            out += "font-family=\"\(escape(family)), PingFang SC, sans-serif\" font-size=\"\(fmt(font.pointSize))\" "
            out += "fill=\"\(hex(color))\">\(escape(line))</text>\n"
        }
        return out
    }

    private static func pathData(for route: EdgeRoute, style: EdgeStyle, horizontal: Bool) -> String {
        let points = route.points
        if style == .curved, points.count == 2 {
            let a = points[0], b = points[1]
            if horizontal {
                let c = (b.x - a.x) * 0.5
                return "M \(fmt(a.x)) \(fmt(a.y)) C \(fmt(a.x + c)) \(fmt(a.y)), \(fmt(b.x - c)) \(fmt(b.y)), \(fmt(b.x)) \(fmt(b.y))"
            } else {
                let c = (b.y - a.y) * 0.5
                return "M \(fmt(a.x)) \(fmt(a.y)) C \(fmt(a.x)) \(fmt(a.y + c)), \(fmt(b.x)) \(fmt(b.y - c)), \(fmt(b.x)) \(fmt(b.y))"
            }
        }
        if style == .straight {
            let first = points[0], last = points[points.count - 1]
            return "M \(fmt(first.x)) \(fmt(first.y)) L \(fmt(last.x)) \(fmt(last.y))"
        }
        if style == .fork {
            if points.count == 2 {
                return "M \(fmt(points[0].x)) \(fmt(points[0].y)) L \(fmt(points[1].x)) \(fmt(points[1].y))"
            }
            if points.count == 4 {
                var d = "M \(fmt(points[0].x)) \(fmt(points[0].y))"
                d += " L \(fmt(points[1].x)) \(fmt(points[1].y))"
                d += " L \(fmt(points[3].x)) \(fmt(points[3].y))"
                return d
            }
        }
        if style == .orthogonal && points.count > 2 {
            let path = CGMutablePath()
            path.move(to: points[0])
            for i in 1..<(points.count - 1) {
                let previous = points[i - 1]
                let current = points[i]
                let next = points[i + 1]
                let inLength = hypot(current.x - previous.x, current.y - previous.y)
                let outLength = hypot(next.x - current.x, next.y - current.y)
                let r = min(13, inLength / 2, outLength / 2)
                if r > 0.5 {
                    path.addArc(tangent1End: current, tangent2End: next, radius: r)
                } else {
                    path.addLine(to: current)
                }
            }
            path.addLine(to: points[points.count - 1])
            return pathData(path)
        }
        var d = "M \(fmt(points[0].x)) \(fmt(points[0].y))"
        for p in points.dropFirst() { d += " L \(fmt(p.x)) \(fmt(p.y))" }
        return d
    }

    private static func arrowAngle(route: EdgeRoute, style: EdgeStyle, horizontal: Bool) -> CGFloat {
        if style == .curved, route.points.count == 2 {
            return horizontal ? 0 : .pi / 2
        }
        if style == .straight, let first = route.points.first, let last = route.points.last {
            return atan2(last.y - first.y, last.x - first.x)
        }
        if style == .fork, route.points.count == 4 {
            let from = route.points[1]
            let to = route.points[3]
            return atan2(to.y - from.y, to.x - from.x)
        }
        return route.arrowAngle
    }

    static func pathData(_ path: CGPath) -> String {
        var d = ""
        path.applyWithBlock { elementPointer in
            let element = elementPointer.pointee
            switch element.type {
            case .moveToPoint:
                d += "M \(fmt(element.points[0].x)) \(fmt(element.points[0].y)) "
            case .addLineToPoint:
                d += "L \(fmt(element.points[0].x)) \(fmt(element.points[0].y)) "
            case .addQuadCurveToPoint:
                d += "Q \(fmt(element.points[0].x)) \(fmt(element.points[0].y)) \(fmt(element.points[1].x)) \(fmt(element.points[1].y)) "
            case .addCurveToPoint:
                d += "C \(fmt(element.points[0].x)) \(fmt(element.points[0].y)) \(fmt(element.points[1].x)) \(fmt(element.points[1].y)) \(fmt(element.points[2].x)) \(fmt(element.points[2].y)) "
            case .closeSubpath:
                d += "Z "
            @unknown default:
                break
            }
        }
        return d.trimmingCharacters(in: .whitespaces)
    }

    private static func fmt(_ value: CGFloat) -> String {
        String(format: "%.2f", value)
    }

    private static func hex(_ color: RGB) -> String {
        let r = Int(round(max(0, min(1, color.r)) * 255))
        let g = Int(round(max(0, min(1, color.g)) * 255))
        let b = Int(round(max(0, min(1, color.b)) * 255))
        return String(format: "#%02X%02X%02X", r, g, b)
    }

    private static func escape(_ text: String) -> String {
        text.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
    }
}
