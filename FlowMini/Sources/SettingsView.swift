import SwiftUI
import AppKit

struct SettingsView: View {
    @ObservedObject var store: Store

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    header
                    appearanceCard
                    canvasCard
                }
                .padding(16)
            }
            .scrollIndicators(.never)

            Divider()

            HStack(spacing: 10) {
                Text("FlowMini 1.0 · 只在本机运行，不联网、不上传")
                    .font(.system(size: 10.5))
                    .foregroundStyle(.secondary)
                Spacer()
                Button("恢复默认外观") { store.resetSettings() }
                    .controlSize(.small)
                Text("⌘N 新建 · ⌘O 打开 · ⌘S 保存 · ⌘E 导出")
                    .font(.system(size: 10.5))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 16)
            .frame(height: 42)
            .background(store.barColor)
        }
        .frame(width: 600, height: 800)
        .background(store.pageColor)
        .preferredColorScheme(store.appearance.colorScheme)
        .tint(store.accent.color)
    }

    // MARK: 头部

    private var header: some View {
        HStack(spacing: 12) {
            Image(nsImage: NSApp.applicationIconImage)
                .resizable()
                .frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text("FlowMini")
                    .font(.system(size: 17, weight: .semibold))
                Text("本地流程图工具 · 不联网 · 不用登录")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(.bottom, 2)
    }

    // MARK: 外观

    private var appearanceCard: some View {
        SettingsCard(title: "界面外观", subtitle: "皮肤、明暗和强调色，只影响 App 本身") {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("明暗").font(.system(size: 12))
                    Spacer()
                    Picker("", selection: $store.appearance) {
                        ForEach(AppearanceMode.allCases) { mode in
                            Text(mode.title).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .frame(width: 250)
                }

                HStack {
                    Text("强调色").font(.system(size: 12))
                    Spacer()
                    HStack(spacing: 9) {
                        ForEach(AccentChoice.allCases) { choice in
                            Button {
                                store.accent = choice
                            } label: {
                                Circle()
                                    .fill(choice.color)
                                    .frame(width: 17, height: 17)
                                    .overlay(
                                        Circle().strokeBorder(
                                            Color.white.opacity(store.accent == choice ? 0.95 : 0),
                                            lineWidth: 2
                                        )
                                    )
                                    .overlay(Circle().strokeBorder(Color.primary.opacity(0.15), lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                            .help(choice.name)
                        }
                    }
                }

                Divider()

                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("皮肤").font(.system(size: 12))
                        Text("快捷组合").font(.system(size: 9.5)).foregroundStyle(.secondary)
                    }
                    .frame(width: 54, alignment: .leading)
                    ForEach(UISkin.all) { skin in
                        skinCard(skin)
                    }
                }
            }
        }
    }

    private func skinCard(_ skin: UISkin) -> some View {
        let active = store.activeSkin?.id == skin.id
        return Button {
            store.apply(skin: skin)
        } label: {
            VStack(spacing: 6) {
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 8).fill(skin.previewBackground)
                    HStack(spacing: 0) {
                        Rectangle().fill(skin.previewSidebar).frame(width: 15)
                        Spacer(minLength: 0)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 8))

                    RoundedRectangle(cornerRadius: 2)
                        .fill(skin.accent.color)
                        .frame(width: 22, height: 13)
                        .offset(x: 38, y: 6)

                    RoundedRectangle(cornerRadius: 2)
                        .fill(skin.accent.color.opacity(0.35))
                        .frame(width: 16, height: 4)
                        .offset(x: 22, y: -12)
                }
                .frame(width: 82, height: 46)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .strokeBorder(active ? store.accent.color : Color.primary.opacity(0.10),
                                      lineWidth: active ? 2 : 1)
                )

                Text(skin.name)
                    .font(.system(size: 10.5))
                    .foregroundStyle(active ? Color.primary : Color.secondary)
            }
        }
        .buttonStyle(.plain)
        .help("\(skin.name) · \(skin.appearance.title) · \(skin.accent.name)")
    }

    // MARK: 画布

    private var canvasCard: some View {
        SettingsCard(title: "画布", subtitle: "横向 / 纵向两种排版，四种连线样式") {
            VStack(alignment: .leading, spacing: 12) {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                    ForEach(Theme.presets) { theme in
                        themeSwatch(theme)
                    }
                }

                Divider()

                row("方向") {
                    Picker("", selection: $store.direction) {
                        ForEach(FlowDirection.allCases) { direction in
                            Text(direction.title).tag(direction)
                        }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .frame(width: 150)
                }

                row("连线") {
                    Picker("", selection: $store.edgeStyle) {
                        ForEach(EdgeStyle.allCases) { style in
                            Text(style.title).tag(style)
                        }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .frame(width: 270)
                }

                row("间距") {
                    Picker("", selection: $store.density) {
                        ForEach(Density.allCases) { density in
                            Text(density.title).tag(density)
                        }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .frame(width: 210)
                }

                HStack(spacing: 6) {
                    Text(store.edgeStyle.hint)
                    Spacer()
                    Text("皮肤会连画布配色一起换")
                }
                .font(.system(size: 10.5))
                .foregroundStyle(.secondary)
            }
        }
    }

    private func row<Content: View>(_ label: String, @ViewBuilder content: () -> Content) -> some View {
        HStack {
            Text(label).font(.system(size: 12))
            Spacer()
            content()
        }
    }

    private func themeSwatch(_ theme: Theme) -> some View {
        let active = store.themeID == theme.id
        return Button {
            store.themeID = theme.id
        } label: {
            VStack(spacing: 5) {
                MiniDiagram(theme: theme, style: store.edgeStyle)
                    .frame(height: 46)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .strokeBorder(active ? store.accent.color : Color.primary.opacity(0.10),
                                          lineWidth: active ? 2 : 1)
                    )
                Text(theme.name)
                    .font(.system(size: 10.5))
                    .foregroundStyle(active ? Color.primary : Color.secondary)
            }
        }
        .buttonStyle(.plain)
        .help("\(theme.name) · \(theme.blurb)")
    }

    private func infoRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
            Spacer()
            Text(value)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.trailing)
        }
    }
}

// MARK: - 卡片容器

struct SettingsCard<Content: View>: View {
    var title: String
    var subtitle: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                if let subtitle {
                    Text(subtitle)
                        .font(.system(size: 10.5))
                        .foregroundStyle(.secondary)
                }
            }
            content
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.6))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(Color.primary.opacity(0.06))
        )
    }
}

// MARK: - 迷你示意图

struct MiniDiagram: View {
    var theme: Theme
    var style: EdgeStyle

    var body: some View {
        Canvas { context, size in
            let full = CGRect(origin: .zero, size: size)
            context.fill(Path(roundedRect: full, cornerRadius: 8), with: .color(Color(theme.bg)))

            let w = size.width
            let h = size.height
            let a = CGRect(x: w * 0.08, y: h * 0.37, width: w * 0.26, height: h * 0.26)
            let b = CGRect(x: w * 0.64, y: h * 0.13, width: w * 0.26, height: h * 0.26)
            let c = CGRect(x: w * 0.64, y: h * 0.61, width: w * 0.26, height: h * 0.26)
            let mid = w * 0.50

            var edges = Path()
            switch style {
            case .orthogonal:
                edges.move(to: CGPoint(x: a.maxX, y: a.midY))
                edges.addLine(to: CGPoint(x: mid, y: a.midY))
                edges.addLine(to: CGPoint(x: mid, y: b.midY))
                edges.addLine(to: CGPoint(x: b.minX, y: b.midY))
                edges.move(to: CGPoint(x: mid, y: a.midY))
                edges.addLine(to: CGPoint(x: mid, y: c.midY))
                edges.addLine(to: CGPoint(x: c.minX, y: c.midY))
            case .fork:
                edges.move(to: CGPoint(x: a.maxX, y: a.midY))
                edges.addLine(to: CGPoint(x: mid, y: a.midY))
                edges.addLine(to: CGPoint(x: b.minX, y: b.midY))
                edges.move(to: CGPoint(x: mid, y: a.midY))
                edges.addLine(to: CGPoint(x: c.minX, y: c.midY))
            case .curved:
                edges.move(to: CGPoint(x: a.maxX, y: a.midY))
                edges.addCurve(to: CGPoint(x: b.minX, y: b.midY),
                               control1: CGPoint(x: mid, y: a.midY),
                               control2: CGPoint(x: mid, y: b.midY))
                edges.move(to: CGPoint(x: a.maxX, y: a.midY))
                edges.addCurve(to: CGPoint(x: c.minX, y: c.midY),
                               control1: CGPoint(x: mid, y: a.midY),
                               control2: CGPoint(x: mid, y: c.midY))
            case .straight:
                edges.move(to: CGPoint(x: a.maxX, y: a.midY))
                edges.addLine(to: CGPoint(x: b.minX, y: b.midY))
                edges.move(to: CGPoint(x: a.maxX, y: a.midY))
                edges.addLine(to: CGPoint(x: c.minX, y: c.midY))
            }
            context.stroke(edges, with: .color(Color(theme.edge)), lineWidth: 1)

            for index in 0..<3 {
                let rect = index == 0 ? a : (index == 1 ? b : c)
                let path = Path(roundedRect: rect, cornerRadius: min(rect.height * 0.3, 4))
                let fill = theme.cardPalette.isEmpty
                    ? theme.card
                    : theme.cardPalette[index % theme.cardPalette.count]
                context.fill(path, with: .color(Color(fill)))
                context.stroke(path, with: .color(Color(theme.stroke)), lineWidth: 1)
            }
        }
    }
}
