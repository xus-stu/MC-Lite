import SwiftUI
import AppKit

// MARK: - 画布命令

struct CanvasCommand: Equatable {
    enum Kind: Equatable {
        case none, fit, zoomIn, zoomOut, actual
    }
    var token: Int = 0
    var kind: Kind = .none
}

// MARK: - AppKit 画布

final class DiagramNSView: NSView {

    var result: LayoutResult? { didSet { needsDisplay = true } }
    var theme: Theme = Theme.presets[0] { didSet { needsDisplay = true } }
    var style: EdgeStyle = .orthogonal { didSet { needsDisplay = true } }
    var onZoomChange: ((Int) -> Void)?

    private var autoFit = true
    private var manualZoom: CGFloat = 1
    private var pan: CGPoint = .zero
    private var dragOrigin: CGPoint?
    private var lastToken = -1
    private var reportedZoom = -1

    override var isFlipped: Bool { true }
    override var acceptsFirstResponder: Bool { true }
    override var isOpaque: Bool { true }

    // MARK: 缩放

    var zoom: CGFloat {
        guard autoFit, let result, result.size.width > 1, result.size.height > 1 else {
            return manualZoom
        }
        let zx = (bounds.width - 96) / result.size.width
        let zy = (bounds.height - 96) / result.size.height
        return max(0.1, min(1.25, min(zx, zy)))
    }

    func fit() {
        autoFit = true
        pan = .zero
        reportZoom()
        needsDisplay = true
    }

    func zoomBy(_ factor: CGFloat) {
        manualZoom = max(0.15, min(4, zoom * factor))
        autoFit = false
        reportZoom()
        needsDisplay = true
    }

    func apply(_ command: CanvasCommand) {
        guard command.token != lastToken else { return }
        lastToken = command.token
        switch command.kind {
        case .fit:
            fit()
        case .zoomIn:
            zoomBy(1.25)
        case .zoomOut:
            zoomBy(0.8)
        case .actual:
            manualZoom = 1
            autoFit = false
            pan = .zero
            reportZoom()
            needsDisplay = true
        case .none:
            break
        }
    }

    private func reportZoom() {
        let percent = Int((zoom * 100).rounded())
        guard percent != reportedZoom else { return }
        reportedZoom = percent
        let callback = onZoomChange
        DispatchQueue.main.async { callback?(percent) }
    }

    override func layout() {
        super.layout()
        if autoFit { reportZoom() }
    }

    // MARK: 绘制

    override func draw(_ dirtyRect: NSRect) {
        guard let ctx = NSGraphicsContext.current?.cgContext else { return }
        guard let result else {
            Theme.presets[0].bg.ns.setFill()
            dirtyRect.fill()
            return
        }

        let renderer = DiagramRenderer(layout: result, theme: theme, style: style)
        renderer.drawBackground(in: ctx, canvas: bounds.size, grid: true)

        let z = zoom
        let ox = (bounds.width - result.size.width * z) / 2 + pan.x
        let oy = (bounds.height - result.size.height * z) / 2 + pan.y

        ctx.saveGState()
        ctx.translateBy(x: ox, y: oy)
        ctx.scaleBy(x: z, y: z)
        renderer.drawContent(in: ctx)
        ctx.restoreGState()
    }

    // MARK: 交互

    override func mouseDown(with event: NSEvent) {
        window?.makeFirstResponder(self)
        if event.clickCount == 2 {
            fit()
            return
        }
        dragOrigin = convert(event.locationInWindow, from: nil)
        NSCursor.closedHand.push()
    }

    override func mouseDragged(with event: NSEvent) {
        guard let origin = dragOrigin else { return }
        let point = convert(event.locationInWindow, from: nil)
        if autoFit {
            manualZoom = zoom
            autoFit = false
        }
        pan.x += point.x - origin.x
        pan.y += point.y - origin.y
        dragOrigin = point
        needsDisplay = true
    }

    override func mouseUp(with event: NSEvent) {
        if dragOrigin != nil {
            dragOrigin = nil
            NSCursor.pop()
        }
    }

    override func scrollWheel(with event: NSEvent) {
        if event.modifierFlags.contains(.command) {
            zoomBy(1 + event.scrollingDeltaY * 0.01)
            return
        }
        if autoFit {
            manualZoom = zoom
            autoFit = false
        }
        let scale: CGFloat = event.hasPreciseScrollingDeltas ? 1 : 8
        pan.x += event.scrollingDeltaX * scale
        pan.y += event.scrollingDeltaY * scale
        needsDisplay = true
    }

    override func magnify(with event: NSEvent) {
        zoomBy(1 + event.magnification)
    }

    override func resetCursorRects() {
        addCursorRect(bounds, cursor: .openHand)
    }
}

// MARK: - SwiftUI 包装

struct DiagramCanvas: NSViewRepresentable {
    var layout: LayoutResult
    var theme: Theme
    var style: EdgeStyle
    var command: CanvasCommand
    var onZoom: (Int) -> Void

    func makeNSView(context: Context) -> DiagramNSView {
        let view = DiagramNSView()
        view.theme = theme
        view.style = style
        view.result = layout
        view.onZoomChange = onZoom
        return view
    }

    func updateNSView(_ view: DiagramNSView, context: Context) {
        view.theme = theme
        view.style = style
        view.result = layout
        view.onZoomChange = onZoom
        view.apply(command)
    }
}
