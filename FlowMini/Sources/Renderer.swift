import Foundation
import AppKit
import CoreGraphics
import CoreText

// MARK: - 形状

enum NodePathBuilder {

    static func path(shape: NodeShape, rect: CGRect, corner: CGFloat) -> CGPath {
        let p = CGMutablePath()
        let x = rect.minX, y = rect.minY, w = rect.width, h = rect.height

        switch shape {
        case .box, .docs:
            let r = min(corner, min(w, h) / 2)
            p.addRoundedRect(in: rect, cornerWidth: r, cornerHeight: r)

        case .round, .pill:
            let r = min(h / 2, w / 2)
            p.addRoundedRect(in: rect, cornerWidth: r, cornerHeight: r)

        case .diamond:
            p.move(to: CGPoint(x: rect.midX, y: y))
            p.addLine(to: CGPoint(x: x + w, y: rect.midY))
            p.addLine(to: CGPoint(x: rect.midX, y: y + h))
            p.addLine(to: CGPoint(x: x, y: rect.midY))
            p.closeSubpath()

        case .circle:
            p.addEllipse(in: rect)

        case .hexagon:
            let inset = min(w * 0.18, h * 0.5)
            p.move(to: CGPoint(x: x + inset, y: y))
            p.addLine(to: CGPoint(x: x + w - inset, y: y))
            p.addLine(to: CGPoint(x: x + w, y: rect.midY))
            p.addLine(to: CGPoint(x: x + w - inset, y: y + h))
            p.addLine(to: CGPoint(x: x + inset, y: y + h))
            p.addLine(to: CGPoint(x: x, y: rect.midY))
            p.closeSubpath()

        case .parallelogram:
            let skew = min(14, w * 0.16)
            p.move(to: CGPoint(x: x + skew, y: y))
            p.addLine(to: CGPoint(x: x + w, y: y))
            p.addLine(to: CGPoint(x: x + w - skew, y: y + h))
            p.addLine(to: CGPoint(x: x, y: y + h))
            p.closeSubpath()
        }

        return p
    }

    /// 文档形状（底边波浪），单独绘制以免上面的圆角矩形丢掉波浪
    static func docsPath(rect: CGRect, corner: CGFloat) -> CGPath {
        let p = CGMutablePath()
        let x = rect.minX, y = rect.minY, w = rect.width, h = rect.height
        let wave = min(9, h * 0.18)
        let r = min(corner, 4)

        p.move(to: CGPoint(x: x + r, y: y))
        p.addLine(to: CGPoint(x: x + w - r, y: y))
        p.addQuadCurve(to: CGPoint(x: x + w, y: y + r), control: CGPoint(x: x + w, y: y))
        p.addLine(to: CGPoint(x: x + w, y: y + h - wave))
        p.addCurve(to: CGPoint(x: x, y: y + h - wave * 0.55),
                   control1: CGPoint(x: x + w * 0.62, y: y + h - wave * 2.1),
                   control2: CGPoint(x: x + w * 0.3, y: y + h + wave * 0.9))
        p.addLine(to: CGPoint(x: x, y: y + r))
        p.addQuadCurve(to: CGPoint(x: x + r, y: y), control: CGPoint(x: x, y: y))
        p.closeSubpath()
        return p
    }
}

// MARK: - 渲染

struct DiagramRenderer {
    var layout: LayoutResult
    var theme: Theme
    var style: EdgeStyle
    var drawGrid: Bool = true

    private var horizontal: Bool { layout.horizontal }

    func draw(in ctx: CGContext, canvas: CGSize) {
        drawBackground(in: ctx, canvas: canvas, grid: drawGrid)
        drawContent(in: ctx)
    }

    func drawBackground(in ctx: CGContext, canvas: CGSize, grid: Bool) {
        ctx.saveGState()
        ctx.setFillColor(theme.bg.cg)
        ctx.fill(CGRect(origin: .zero, size: canvas))
        ctx.restoreGState()
        if grid { drawBackgroundGrid(ctx, canvas) }
    }

    func drawContent(in ctx: CGContext) {
        drawEdges(ctx)
        drawNodes(ctx)
        drawEdgeLabels(ctx)
    }

    // MARK: 网格

    private func drawBackgroundGrid(_ ctx: CGContext, _ size: CGSize) {
        guard theme.gridStyle != .none else { return }
        let spacing: CGFloat = 26
        ctx.saveGState()
        ctx.setFillColor(theme.grid.cg)
        ctx.setStrokeColor(theme.grid.cg)
        ctx.setLineWidth(0.6)

        switch theme.gridStyle {
        case .dots:
            let path = CGMutablePath()
            var x: CGFloat = spacing
            while x < size.width {
                var y: CGFloat = spacing
                while y < size.height {
                    path.addRect(CGRect(x: x - 0.9, y: y - 0.9, width: 1.8, height: 1.8))
                    y += spacing
                }
                x += spacing
            }
            ctx.addPath(path)
            ctx.fillPath()
        case .lines:
            var x: CGFloat = spacing
            while x < size.width {
                ctx.move(to: CGPoint(x: x, y: 0))
                ctx.addLine(to: CGPoint(x: x, y: size.height))
                x += spacing
            }
            var y: CGFloat = spacing
            while y < size.height {
                ctx.move(to: CGPoint(x: 0, y: y))
                ctx.addLine(to: CGPoint(x: size.width, y: y))
                y += spacing
            }
            ctx.strokePath()
        case .none:
            break
        }
        ctx.restoreGState()
    }

    // MARK: 连线

    private func drawEdges(_ ctx: CGContext) {
        for route in layout.routes {
            let path = edgePath(route)
            ctx.saveGState()
            ctx.setStrokeColor(theme.edge.cg)
            ctx.setLineWidth(max(1, theme.lineWidth * 0.95))
            ctx.setLineCap(.round)
            ctx.setLineJoin(.round)
            if route.edge.dashed {
                ctx.setLineDash(phase: 0, lengths: [6, 4.5])
            }

            if theme.sketch {
                ctx.addPath(path)
                ctx.strokePath()
                ctx.saveGState()
                ctx.translateBy(x: 1.1, y: -0.7)
                ctx.setAlpha(0.42)
                ctx.addPath(path)
                ctx.strokePath()
                ctx.restoreGState()
            } else {
                ctx.addPath(path)
                ctx.strokePath()
            }
            ctx.restoreGState()

            if route.edge.arrow {
                let angle = arrowAngle(for: route)
                let end = route.points.last ?? .zero
                drawArrow(ctx, at: end, angle: angle)
            }
        }
    }

    private func edgePath(_ route: EdgeRoute) -> CGPath {
        let points = route.points
        let path = CGMutablePath()
        guard let first = points.first, let last = points.last else { return path }

        if style == .curved {
            if points.count == 2 {
                path.move(to: first)
                let dx = last.x - first.x
                let dy = last.y - first.y
                if horizontal {
                    let c = dx * 0.5
                    path.addCurve(to: last,
                                  control1: CGPoint(x: first.x + c, y: first.y),
                                  control2: CGPoint(x: last.x - c, y: last.y))
                } else {
                    let c = dy * 0.5
                    path.addCurve(to: last,
                                  control1: CGPoint(x: first.x, y: first.y + c),
                                  control2: CGPoint(x: last.x, y: last.y - c))
                }
                return path
            }
            // 多点：用平滑曲线穿过各拐点
            path.move(to: first)
            var previous = first
            for i in 1..<(points.count - 1) {
                let current = points[i]
                let next = points[i + 1]
                let mid = CGPoint(x: (current.x + next.x) / 2, y: (current.y + next.y) / 2)
                if i == 1 {
                    path.addQuadCurve(to: mid, control: current)
                } else {
                    path.addQuadCurve(to: mid, control: current)
                }
                previous = mid
            }
            path.addLine(to: last)
            _ = previous
            return path
        }

        if style == .straight {
            path.move(to: first)
            path.addLine(to: last)
            return path
        }

        if style == .fork {
            if points.count == 2 {
                path.move(to: first)
                path.addLine(to: last)
            } else if points.count == 4 {
                path.move(to: first)
                path.addLine(to: points[1])
                path.addLine(to: last)
            } else {
                path.move(to: first)
                for p in points.dropFirst() { path.addLine(to: p) }
            }
            return path
        }

        if points.count < 3 {
            path.move(to: first)
            for p in points.dropFirst() { path.addLine(to: p) }
            return path
        }

        // 正交：圆角折线
        path.move(to: first)
        let radius: CGFloat = 13
        if points.count > 2 {
            for i in 1..<(points.count - 1) {
                let previous = points[i - 1]
                let current = points[i]
                let next = points[i + 1]
                let inLength = hypot(current.x - previous.x, current.y - previous.y)
                let outLength = hypot(next.x - current.x, next.y - current.y)
                let r = min(radius, inLength / 2, outLength / 2)
                if r > 0.5 {
                    path.addArc(tangent1End: current, tangent2End: next, radius: r)
                } else {
                    path.addLine(to: current)
                }
            }
        }
        path.addLine(to: last)
        return path
    }

    private func arrowAngle(for route: EdgeRoute) -> CGFloat {
        guard route.edge.arrow else { return 0 }
        if style == .curved, route.points.count >= 2 {
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

    private func drawArrow(_ ctx: CGContext, at point: CGPoint, angle: CGFloat) {
        let length: CGFloat = 9
        let half: CGFloat = 4.6
        ctx.saveGState()
        ctx.translateBy(x: point.x - cos(angle) * 1.2, y: point.y - sin(angle) * 1.2)
        ctx.rotate(by: angle)
        ctx.move(to: .zero)
        ctx.addLine(to: CGPoint(x: -length, y: -half))
        ctx.addLine(to: CGPoint(x: -length * 0.72, y: 0))
        ctx.addLine(to: CGPoint(x: -length, y: half))
        ctx.closePath()
        ctx.setFillColor(theme.edge.cg)
        ctx.fillPath()
        ctx.restoreGState()
    }

    // MARK: 节点

    private func drawNodes(_ ctx: CGContext) {
        for (index, box) in layout.boxes.enumerated() {
            let rect = box.rect
            let path = box.node.shape == .docs
                ? NodePathBuilder.docsPath(rect: rect, corner: theme.corner)
                : NodePathBuilder.path(shape: box.node.shape, rect: rect, corner: theme.corner)

            ctx.saveGState()
            if theme.sketch {
                let center = CGPoint(x: rect.midX, y: rect.midY)
                ctx.translateBy(x: center.x, y: center.y)
                ctx.rotate(by: stableTilt(box.node.id))
                ctx.translateBy(x: -center.x, y: -center.y)
            }

            let fill = fillColor(for: index)
            if theme.shadow > 0 {
                let shadowColor = theme.isDark
                    ? RGB(0x000000, 0.55).cg
                    : theme.stroke.alpha(0.16).cg
                ctx.setShadow(offset: CGSize(width: 0, height: 2.2), blur: theme.shadow, color: shadowColor)
            }
            ctx.setFillColor(fill.cg)
            ctx.addPath(path)
            ctx.fillPath()
            ctx.setShadow(offset: .zero, blur: 0, color: nil)

            ctx.setStrokeColor(theme.stroke.cg)
            ctx.setLineWidth(theme.lineWidth)
            ctx.setLineJoin(.round)
            ctx.addPath(path)
            ctx.strokePath()

            if theme.sketch {
                ctx.saveGState()
                ctx.translateBy(x: 1.2, y: 0.6)
                ctx.setAlpha(0.38)
                ctx.addPath(path)
                ctx.strokePath()
                ctx.restoreGState()
            }
            ctx.restoreGState()

            drawNodeLabel(ctx, box: box, fill: fill)
        }
    }

    private func fillColor(for index: Int) -> RGB {
        guard !theme.cardPalette.isEmpty else { return theme.card }
        return theme.cardPalette[index % theme.cardPalette.count]
    }

    private func drawNodeLabel(_ ctx: CGContext, box: NodeBox, fill: RGB) {
        let font = theme.font()
        let color = contrastText(on: fill)
        let insetX: CGFloat = 10
        let insetY: CGFloat = 8
        let rect = box.rect.insetBy(dx: insetX, dy: insetY)
        TextDraw.draw(box.node.label, in: rect, font: font, color: color, ctx: ctx, lineHeight: Metrics.lineHeight(font))
    }

    private func contrastText(on fill: RGB) -> RGB {
        // 浅色节点用主题文字色；深色节点若主题为浅色主题则自动转白
        let luminance = 0.299 * fill.r + 0.587 * fill.g + 0.114 * fill.b
        if luminance < 0.45 && !theme.isDark {
            return RGB(0xFFFFFF, 0.94)
        }
        return theme.text
    }

    // MARK: 连线标签

    private func drawEdgeLabels(_ ctx: CGContext) {
        let font = theme.font(size: max(10.5, theme.fontSize - 2.2))
        for route in layout.routes {
            guard let text = route.edge.label, !text.isEmpty, let center = route.labelPoint else { continue }
            let size = TextDraw.measure(text, font: font, lineHeight: Metrics.lineHeight(font))
            let chip = CGRect(x: center.x - size.width / 2 - 7,
                              y: center.y - size.height / 2 - 3,
                              width: size.width + 14,
                              height: size.height + 6)
            let chipPath = CGPath(roundedRect: chip, cornerWidth: 5, cornerHeight: 5, transform: nil)

            ctx.saveGState()
            ctx.setFillColor(theme.labelBackground.alpha(0.95).cg)
            ctx.addPath(chipPath)
            ctx.fillPath()
            if theme.isDark {
                ctx.setStrokeColor(theme.stroke.alpha(0.35).cg)
                ctx.setLineWidth(0.8)
                ctx.addPath(chipPath)
                ctx.strokePath()
            }
            ctx.restoreGState()

            TextDraw.draw(text,
                          in: chip.insetBy(dx: 7, dy: 3),
                          font: font,
                          color: theme.text.alpha(0.86),
                          ctx: ctx,
                          lineHeight: Metrics.lineHeight(font))
        }
    }

    private func stableTilt(_ id: String) -> CGFloat {
        var hash: UInt64 = 5381
        for byte in id.utf8 { hash = (hash &* 33) &+ UInt64(byte) }
        let unit = CGFloat(hash % 1000) / 1000
        return (unit - 0.5) * 0.022
    }
}

// MARK: - 文字

enum TextDraw {

    static func measure(_ text: String, font: NSFont, lineHeight: CGFloat) -> CGSize {
        let lines = text.isEmpty ? [""] : text.components(separatedBy: "\n")
        var width: CGFloat = 0
        for line in lines {
            let w = (line as NSString).size(withAttributes: [.font: font]).width
            width = max(width, w)
        }
        return CGSize(width: ceil(width), height: ceil(CGFloat(lines.count) * lineHeight))
    }

    static func draw(_ text: String,
                     in rect: CGRect,
                     font: NSFont,
                     color: RGB,
                     ctx: CGContext,
                     lineHeight: CGFloat) {
        let lines = text.isEmpty ? [""] : text.components(separatedBy: "\n")
        let total = CGFloat(lines.count) * lineHeight
        let top = rect.midY - total / 2

        ctx.saveGState()
        ctx.setFillColor(color.cg)
        ctx.textMatrix = CGAffineTransform(scaleX: 1, y: -1)

        for (index, line) in lines.enumerated() {
            let attr = NSAttributedString(string: line, attributes: [.font: font, .foregroundColor: color.ns])
            let ctLine = CTLineCreateWithAttributedString(attr)
            var ascent: CGFloat = 0
            var descent: CGFloat = 0
            var leading: CGFloat = 0
            let width = CGFloat(CTLineGetTypographicBounds(ctLine, &ascent, &descent, &leading))

            let centerY = top + lineHeight * CGFloat(index) + lineHeight / 2
            let baseline = centerY + (ascent - descent) / 2
            let x = rect.midX - width / 2

            ctx.textPosition = CGPoint(x: x, y: baseline)
            CTLineDraw(ctLine, ctx)
        }
        ctx.restoreGState()
    }
}
