import SwiftUI
import AppKit

// MARK: - 界面外观

enum AppearanceMode: String, CaseIterable, Identifiable, Codable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var title: String {
        switch self {
        case .system: return "跟随系统"
        case .light: return "浅色"
        case .dark: return "深色"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

enum AccentChoice: String, CaseIterable, Identifiable, Codable {
    case blue, mint, orange, rose, violet, graphite

    var id: String { rawValue }

    var name: String {
        switch self {
        case .blue: return "海蓝"
        case .mint: return "薄荷"
        case .orange: return "暖橙"
        case .rose: return "玫瑰"
        case .violet: return "紫罗兰"
        case .graphite: return "石墨"
        }
    }

    var color: Color {
        switch self {
        case .blue: return Color(RGB(0x2F7CF6))
        case .mint: return Color(RGB(0x12B886))
        case .orange: return Color(RGB(0xF08A24))
        case .rose: return Color(RGB(0xE8557A))
        case .violet: return Color(RGB(0x8B5CF6))
        case .graphite: return Color(RGB(0x5B6472))
        }
    }
}

/// 「皮肤」= 明暗 + 强调色 的快捷组合
struct UISkin: Identifiable {
    var id: String
    var name: String
    var appearance: AppearanceMode
    var accent: AccentChoice
    var previewBackground: Color
    var previewSidebar: Color
    /// 主界面配色（nil = 使用系统原生颜色）
    var page: Color? = nil
    var panel: Color? = nil
    var bar: Color? = nil
    var themeID: String = "ink"

    static let all: [UISkin] = [
        UISkin(id: "classic", name: "默认", appearance: .system, accent: .blue,
               previewBackground: Color(RGB(0xF7F7F6)), previewSidebar: Color(RGB(0xE8E8E5))),
        UISkin(id: "paper", name: "纸白", appearance: .light, accent: .orange,
               previewBackground: Color(RGB(0xF8F1E4)), previewSidebar: Color(RGB(0xEADFC8)),
               page: Color(RGB(0xF4E8D2)), panel: Color(RGB(0xFFFAEC)), bar: Color(RGB(0xEBDCC0)),
               themeID: "paper"),
        UISkin(id: "forest", name: "松林", appearance: .light, accent: .mint,
               previewBackground: Color(RGB(0xF2F7F0)), previewSidebar: Color(RGB(0xDCEBD6)),
               page: Color(RGB(0xE4EFDC)), panel: Color(RGB(0xF8FCF5)), bar: Color(RGB(0xD5E6C9)),
               themeID: "bamboo"),
        UISkin(id: "midnight", name: "午夜", appearance: .dark, accent: .violet,
               previewBackground: Color(RGB(0x191C22)), previewSidebar: Color(RGB(0x0E1014)),
               page: Color(RGB(0x0B0D11)), panel: Color(RGB(0x15181F)), bar: Color(RGB(0x07080B)),
               themeID: "deep"),
        UISkin(id: "neon", name: "霓虹", appearance: .dark, accent: .rose,
               previewBackground: Color(RGB(0x0B0C12)), previewSidebar: Color(RGB(0x06070B)),
               page: Color(RGB(0x050609)), panel: Color(RGB(0x0C0E15)), bar: Color(RGB(0x030407)),
               themeID: "neon")
    ]

    static func matching(appearance: AppearanceMode, accent: AccentChoice) -> UISkin? {
        all.first { $0.appearance == appearance && $0.accent == accent }
    }
}
