import Foundation
import AppKit
import CoreGraphics

// MARK: - 颜色

struct RGB: Hashable {
    var r: CGFloat
    var g: CGFloat
    var b: CGFloat
    var a: CGFloat

    init(_ hex: UInt32, _ alpha: CGFloat = 1) {
        self.r = CGFloat((hex >> 16) & 0xFF) / 255
        self.g = CGFloat((hex >> 8) & 0xFF) / 255
        self.b = CGFloat(hex & 0xFF) / 255
        self.a = alpha
    }

    init(r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat = 1) {
        self.r = r; self.g = g; self.b = b; self.a = a
    }

    var cg: CGColor { CGColor(srgbRed: r, green: g, blue: b, alpha: a) }
    var ns: NSColor { NSColor(srgbRed: r, green: g, blue: b, alpha: a) }

    func alpha(_ value: CGFloat) -> RGB { RGB(r: r, g: g, b: b, a: value) }

    func mix(_ other: RGB, _ t: CGFloat) -> RGB {
        RGB(r: r + (other.r - r) * t,
            g: g + (other.g - g) * t,
            b: b + (other.b - b) * t,
            a: a + (other.a - a) * t)
    }

    func lighten(_ t: CGFloat) -> RGB { mix(RGB(0xFFFFFF), t) }
    func darken(_ t: CGFloat) -> RGB { mix(RGB(0x000000), t) }
}

// MARK: - 主题

enum GridStyle: String, Hashable {
    case none, dots, lines
}

enum ThemeWeight: String, Hashable {
    case regular, medium, semibold

    var ns: NSFont.Weight {
        switch self {
        case .regular: return .regular
        case .medium: return .medium
        case .semibold: return .semibold
        }
    }
}

struct Theme: Identifiable, Hashable {
    var id: String
    var name: String
    var blurb: String
    var bg: RGB
    var card: RGB
    var cardPalette: [RGB] = []
    var stroke: RGB
    var text: RGB
    var edge: RGB
    var accent: RGB
    var grid: RGB = RGB(0x000000, 0.06)
    var gridStyle: GridStyle = .none
    var corner: CGFloat = 6
    var lineWidth: CGFloat = 1.3
    var fontName: String? = nil
    var fontSize: CGFloat = 13.5
    var fontWeight: ThemeWeight = .regular
    var shadow: CGFloat = 0
    var sketch: Bool = false
    var labelBg: RGB? = nil
    var isDark: Bool = false
    var reference: String

    var labelBackground: RGB { labelBg ?? bg }

    func font(size: CGFloat? = nil) -> NSFont {
        let s = size ?? fontSize
        var base: NSFont
        if let name = fontName, let f = NSFont(name: name, size: s) {
            base = f
            if fontWeight != .regular {
                let traits = NSFontTraitMask.boldFontMask
                base = NSFontManager.shared.convert(base, toHaveTrait: traits)
            }
            return base
        }
        return NSFont.systemFont(ofSize: s, weight: fontWeight.ns)
    }

    func monoFont(size: CGFloat) -> NSFont {
        NSFont.monospacedSystemFont(ofSize: size, weight: .medium)
    }

    static func named(_ id: String) -> Theme {
        presets.first { $0.id == id } ?? presets[0]
    }

    static let presets: [Theme] = [
        Theme(id: "ink", name: "极简", blurb: "白底 · 细线 · 无装饰",
              bg: RGB(0xFFFFFF), card: RGB(0xFFFFFF), stroke: RGB(0x1A1A1A),
              text: RGB(0x161616), edge: RGB(0x3C3C3C), accent: RGB(0x111111),
              grid: RGB(0x000000, 0.05), gridStyle: .none, corner: 4, lineWidth: 1.35,
              fontName: nil, fontSize: 13.5, fontWeight: .regular, shadow: 0,
              labelBg: RGB(0xFFFFFF), reference: "Minimal"),

        Theme(id: "paper", name: "纸张", blurb: "米白纸感 · 宋体 · 墨线",
              bg: RGB(0xF5EFE3), card: RGB(0xFFFBF2), stroke: RGB(0x2A211A),
              text: RGB(0x2A211A), edge: RGB(0x4B3D31), accent: RGB(0xB4552D),
              grid: RGB(0x8A755C, 0.10), gridStyle: .none, corner: 7, lineWidth: 1.5,
              fontName: "STSong", fontSize: 14.5, fontWeight: .regular, shadow: 4,
              labelBg: RGB(0xF5EFE3), reference: "Paper"),

        Theme(id: "pastel", name: "糖果", blurb: "淡彩卡片 · 圆角 · 投影",
              bg: RGB(0xFFF8F2), card: RGB(0xFFFFFF),
              cardPalette: [RGB(0xFFE2D2), RGB(0xDDF3E3), RGB(0xE2E8FF), RGB(0xFFF0C2), RGB(0xF4E2FF)],
              stroke: RGB(0x6A4E42), text: RGB(0x3B2B25), edge: RGB(0xA38C80),
              accent: RGB(0xE8846B), grid: RGB(0xE8DCD2, 0.5), gridStyle: .none,
              corner: 14, lineWidth: 1.3, fontName: "PingFangSC-Regular", fontSize: 13.5,
              fontWeight: .regular, shadow: 7, labelBg: RGB(0xFFF8F2), reference: "Pastel"),

        Theme(id: "bamboo", name: "青竹", blurb: "浅绿纸面 · 竹青线条",
              bg: RGB(0xF3F7EE), card: RGB(0xFBFEF7), stroke: RGB(0x2F5B3C),
              text: RGB(0x1D3326), edge: RGB(0x4C7A57), accent: RGB(0x12B886),
              grid: RGB(0x2F5B3C, 0.06), gridStyle: .none, corner: 10, lineWidth: 1.45,
              fontName: "PingFangSC-Regular", fontSize: 13.5, fontWeight: .regular, shadow: 0,
              labelBg: RGB(0xF3F7EE), reference: "Bamboo"),

        Theme(id: "mono", name: "网格", blurb: "工程方格纸 · 等宽字",
              bg: RGB(0xF3F3EF), card: RGB(0xFFFFFF), stroke: RGB(0x101010),
              text: RGB(0x101010), edge: RGB(0x101010), accent: RGB(0x101010),
              grid: RGB(0x8C8C82, 0.45), gridStyle: .dots, corner: 0, lineWidth: 1.2,
              fontName: "Menlo-Regular", fontSize: 12.5, fontWeight: .regular, shadow: 0,
              labelBg: RGB(0xF3F3EF), reference: "Grid"),

        Theme(id: "sketch", name: "手绘", blurb: "手写体 · 抖动线条",
              bg: RGB(0xFFFDF3), card: RGB(0xFFFFFF), stroke: RGB(0x33302B),
              text: RGB(0x33302B), edge: RGB(0x3A3730), accent: RGB(0x33302B),
              grid: RGB(0x000000, 0.04), gridStyle: .none, corner: 7, lineWidth: 1.7,
              fontName: "HannotateSC-W5", fontSize: 15, fontWeight: .regular, shadow: 0,
              sketch: true, labelBg: RGB(0xFFFDF3), reference: "Sketch"),

        Theme(id: "blueprint", name: "蓝图", blurb: "深蓝底 · 白色工程线",
              bg: RGB(0x0E2A47), card: RGB(0x123555), stroke: RGB(0x9CC7EA),
              text: RGB(0xE8F4FF), edge: RGB(0x6FA8DC), accent: RGB(0x8ED0FF),
              grid: RGB(0x3A6E9E, 0.65), gridStyle: .lines, corner: 0, lineWidth: 1.2,
              fontName: "Menlo-Regular", fontSize: 12.5, fontWeight: .regular, shadow: 0,
              labelBg: RGB(0x0E2A47), isDark: true, reference: "Blueprint"),

        Theme(id: "deep", name: "深夜", blurb: "深灰底 · 冷色高亮",
              bg: RGB(0x101216), card: RGB(0x181C23), stroke: RGB(0x3D4553),
              text: RGB(0xF2F4F8), edge: RGB(0x5B6474), accent: RGB(0x7AA2FF),
              grid: RGB(0x2F3846, 0.9), gridStyle: .dots, corner: 9, lineWidth: 1.3,
              fontName: "PingFangSC-Regular", fontSize: 13.5, fontWeight: .regular, shadow: 0,
              labelBg: RGB(0x101216), isDark: true, reference: "Midnight"),

        Theme(id: "neon", name: "霓虹", blurb: "纯黑底 · 荧光描边",
              bg: RGB(0x090B10), card: RGB(0x11151F), stroke: RGB(0x2BE8C4),
              text: RGB(0xE8FFF9), edge: RGB(0x6C5CE7), accent: RGB(0xFF4D8D),
              grid: RGB(0x2A3345, 1.0), gridStyle: .dots, corner: 10, lineWidth: 1.4,
              fontName: "PingFangSC-Medium", fontSize: 13.5, fontWeight: .medium, shadow: 9,
              labelBg: RGB(0x090B10), isDark: true, reference: "Neon")
    ]
}

// MARK: - 尺寸

enum Metrics {
    static func lineHeight(_ font: NSFont) -> CGFloat {
        ceil(font.ascender - font.descender + font.leading)
    }

    static func textSize(_ text: String, font: NSFont) -> CGSize {
        let lines = text.isEmpty ? [""] : text.components(separatedBy: "\n")
        var width: CGFloat = 0
        for line in lines {
            let w = (line as NSString).size(withAttributes: [.font: font]).width
            width = max(width, w)
        }
        return CGSize(width: ceil(width), height: ceil(CGFloat(lines.count) * lineHeight(font)))
    }

    static func nodeSize(_ node: FNode, theme: Theme) -> CGSize {
        let font = theme.font()
        let t = textSize(node.label, font: font)
        let w = t.width
        let h = t.height

        switch node.shape {
        case .diamond:
            return CGSize(width: max(96, w * 1.62 + 38), height: max(72, h * 1.95 + 26))
        case .circle:
            let d = max(max(w, h) + 46, 74)
            return CGSize(width: d * 1.04, height: d)
        case .hexagon:
            return CGSize(width: max(94, w + 74), height: max(50, h + 28))
        case .parallelogram:
            return CGSize(width: max(94, w + 62), height: max(50, h + 28))
        case .docs:
            return CGSize(width: max(86, w + 46), height: max(54, h + 32))
        case .box, .round, .pill:
            return CGSize(width: max(74, w + 46), height: max(48, h + 28))
        }
    }
}
