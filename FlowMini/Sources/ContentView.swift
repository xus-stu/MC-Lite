import SwiftUI
import AppKit

extension Color {
    init(_ rgb: RGB) {
        self.init(.sRGB, red: Double(rgb.r), green: Double(rgb.g), blue: Double(rgb.b), opacity: Double(rgb.a))
    }
}

struct ContentView: View {
    @ObservedObject var store: Store
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        GeometryReader { geo in
            HStack(spacing: 0) {
                if store.showEditor {
                    editorPane
                        .frame(width: editorWidth(in: geo.size.width))
                    splitter(container: geo.size.width)
                }
                canvasPane
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(minWidth: 880, minHeight: 540)
        .background(store.pageColor)
        .navigationTitle(store.fileName)
        .toolbar { toolbar }
        .preferredColorScheme(store.appearance.colorScheme)
        .tint(store.accent.color)

    }

    private func editorWidth(in total: CGFloat) -> CGFloat {
        min(max(store.editorWidth, 240), max(240, total - 460))
    }

    private func splitter(container: CGFloat) -> some View {
        ZStack {
            Color.clear
            Rectangle()
                .fill(Color.primary.opacity(0.09))
                .frame(width: 1)
        }
        .frame(width: 7)
        .contentShape(Rectangle())
        .onHover { hovering in
            if hovering { NSCursor.resizeLeftRight.set() } else { NSCursor.arrow.set() }
        }
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    if store.splitDragStart == nil { store.splitDragStart = store.editorWidth }
                    let proposed = (store.splitDragStart ?? 360) + value.translation.width
                    store.editorWidth = min(max(proposed, 240), max(240, container - 460))
                }
                .onEnded { _ in store.splitDragStart = nil }
        )
    }

    // MARK: 编辑区

    private var editorPane: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                Image(systemName: "text.alignleft")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
                Text(store.fileName)
                    .font(.system(size: 12, weight: .medium))
                    .lineLimit(1)
                    .truncationMode(.middle)
                if store.isDirty {
                    Circle()
                        .fill(Color.orange)
                        .frame(width: 6, height: 6)
                        .help("有未保存的修改")
                }
                Spacer()
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) { store.showEditor = false }
                } label: {
                    Image(systemName: "sidebar.leading")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help("隐藏文字面板 ⌥⌘E")

                Button {
                    store.showHelp.toggle()
                } label: {
                    Image(systemName: "questionmark.circle")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help("写法说明和示例")
                .popover(isPresented: $store.showHelp, arrowEdge: .bottom) {
                    HelpView(store: store)
                }
            }
            .padding(.horizontal, 12)
            .frame(height: 36)
            .background(store.barColor)

            Divider()

            TextEditor(text: $store.text)
                .font(.system(size: 12.5, design: .monospaced))
                .lineSpacing(4)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .background(store.panelColor)

            Divider()
            footer
        }
        .background(store.panelColor)
    }

    private var footer: some View {
        HStack(spacing: 5) {
            Text("\(store.graph.nodes.count) 节点")
            Text("·")
            Text("\(store.graph.edges.count) 连线")
            Spacer(minLength: 8)
            if let problem = store.problems.first {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
                Text(problem)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .help(store.problems.joined(separator: "\n"))
            }
        }
        .font(.system(size: 10.5))
        .foregroundStyle(.secondary)
        .padding(.horizontal, 12)
        .frame(height: 26)
        .background(store.barColor)
    }

    // MARK: 画布

    private var canvasPane: some View {
        ZStack {
            DiagramCanvas(layout: store.layout,
                          theme: store.theme,
                          style: store.edgeStyle,
                          command: store.command,
                          onZoom: { store.zoomPercent = $0 })

            if store.graph.nodes.isEmpty {
                emptyState
            }
        }
        .overlay(alignment: .topLeading) {
            if !store.showEditor {
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) { store.showEditor = true }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "sidebar.leading")
                        Text("显示文字面板")
                    }
                    .font(.system(size: 11))
                    .padding(.horizontal, 9)
                    .padding(.vertical, 6)
                    .background(.regularMaterial, in: Capsule())
                    .overlay(Capsule().strokeBorder(Color.primary.opacity(0.07)))
                }
                .buttonStyle(.plain)
                .padding(12)
                .help("显示文字面板 ⌥⌘E")
            }
        }
        .overlay(alignment: .bottomLeading) {
            palette.padding(14)
        }
        .overlay(alignment: .bottomTrailing) {
            zoomBar.padding(14)
        }
        .overlay(alignment: .top) {
            if let toast = store.toast {
                Text(toast)
                    .font(.system(size: 12, weight: .medium))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(.regularMaterial, in: Capsule())
                    .overlay(Capsule().strokeBorder(Color.primary.opacity(0.08)))
                    .padding(.top, 14)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .clipped()
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "arrow.triangle.branch")
                .font(.system(size: 30, weight: .light))
                .foregroundStyle(.secondary)
            Text("左边写一行  a -> b")
                .font(.system(size: 13, weight: .medium))
            Text("每个箭头就是一条连线，写几行就是一张图")
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
            HStack(spacing: 8) {
                Button("最小示例") { store.replace(with: Samples.minimal) }
                Button("入门示例") { store.replace(with: Samples.welcome) }
            }
            .controlSize(.small)
            .padding(.top, 2)
        }
        .padding(24)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.primary.opacity(0.06)))
    }

    private var palette: some View {
        HStack(spacing: 7) {
            ForEach(Theme.presets) { theme in
                Button {
                    store.themeID = theme.id
                } label: {
                    Circle()
                        .fill(Color(theme.bg))
                        .overlay(Circle().strokeBorder(Color(theme.stroke).opacity(0.85), lineWidth: 1))
                        .overlay(
                            Circle()
                                .fill(Color(theme.accent))
                                .frame(width: 6, height: 6)
                        )
                        .frame(width: 22, height: 22)
                        .overlay(
                            Circle()
                                .strokeBorder(store.themeID == theme.id ? store.accent.color : Color.clear, lineWidth: 2)
                                .padding(-3.5)
                        )
                }
                .buttonStyle(.plain)
                .help("\(theme.name) · \(theme.blurb)")
            }
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 7)
        .background(.regularMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(Color.primary.opacity(0.07)))
    }

    private var zoomBar: some View {
        HStack(spacing: 1) {
            Button { store.send(.zoomOut) } label: {
                Image(systemName: "minus").frame(width: 22, height: 20)
            }
            .help("缩小")
            Text("\(store.zoomPercent)%")
                .font(.system(size: 10.5, design: .monospaced))
                .foregroundStyle(.secondary)
                .frame(width: 38)
            Button { store.send(.zoomIn) } label: {
                Image(systemName: "plus").frame(width: 22, height: 20)
            }
            .help("放大")
            Divider().frame(height: 13)
            Button { store.send(.fit) } label: {
                Image(systemName: "arrow.up.left.and.arrow.down.right").frame(width: 22, height: 20)
            }
            .help("适应窗口")
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 6)
        .padding(.vertical, 4)
        .background(.regularMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(Color.primary.opacity(0.07)))
    }

    // MARK: 工具栏

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItemGroup(placement: .navigation) {
            Button { store.newDocument() } label: {
                Image(systemName: "doc.badge.plus")
            }
            .help("新建 ⌘N")

            Button { store.openDocument() } label: {
                Image(systemName: "folder")
            }
            .help("打开 ⌘O")

            Button { store.save() } label: {
                Image(systemName: "square.and.arrow.down")
            }
            .help("保存 ⌘S")
        }

        ToolbarItemGroup(placement: .primaryAction) {
            Picker("方向", selection: $store.direction) {
                ForEach(FlowDirection.allCases) { direction in
                    Text(direction.title).tag(direction)
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .frame(width: 92)
            .help("横向排版 / 纵向排版")

            Menu {
                ForEach(EdgeStyle.allCases) { style in
                    Button {
                        store.edgeStyle = style
                    } label: {
                        Text(style == store.edgeStyle ? "\(style.title) ✓" : style.title)
                    }
                }
            } label: {
                Label("连线", systemImage: "arrow.triangle.branch")
                    .labelStyle(.iconOnly)
            }
            .help("连线样式：直角 / 分叉 / 曲线 / 斜线")

            Menu {
                ForEach(Theme.presets) { theme in
                    Button {
                        store.themeID = theme.id
                    } label: {
                        Text(theme.id == store.themeID ? "\(theme.name) ✓" : theme.name)
                    }
                }
            } label: {
                Label(store.theme.name, systemImage: "paintpalette")
            }
            .help("视觉样式")

            Menu {
                Button("PNG 图片…") { store.exportPNG() }
                Button("PDF 矢量…") { store.exportPDF() }
                Button("SVG 矢量…") { store.exportSVG() }
            } label: {
                Image(systemName: "square.and.arrow.up")
            }
            .help("导出")

            Button {
                openWindow(id: "settings")
            } label: {
                Image(systemName: "gearshape")
            }
            .help("设置 ⌘,")

            Button {
                withAnimation(.easeInOut(duration: 0.18)) { store.showEditor.toggle() }
            } label: {
                Image(systemName: "sidebar.left")
            }
            .help("显示 / 隐藏文字面板")
        }
    }
}

// MARK: - 帮助

struct HelpView: View {
    @ObservedObject var store: Store

    private let rows: [(String, String)] = [
        ("a -> b", "连线，箭头指向 b"),
        ("a -> b: 是", "连线上的文字"),
        ("a --> b", "虚线"),
        ("a -- b", "没有箭头的线"),
        ("a: 显示文字", "节点显示的内容"),
        ("a[diamond]: 判断？", "换形状"),
        ("# 注释", "这一行会被忽略"),
        ("a -> b; a -> c", "一行写多条")
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("写法")
                    .font(.system(size: 13, weight: .semibold))

                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text(row.0)
                                .font(.system(size: 11.5, design: .monospaced))
                                .frame(width: 158, alignment: .leading)
                            Text(row.1)
                                .font(.system(size: 11.5))
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                Text("形状：box  round  pill  diamond  circle  hex  data  docs")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                Divider()

                Text("示例")
                    .font(.system(size: 13, weight: .semibold))

                VStack(alignment: .leading, spacing: 5) {
                    ForEach(Array(Samples.all.enumerated()), id: \.offset) { _, sample in
                        Button(sample.title) {
                            store.replace(with: sample.text)
                            store.showHelp = false
                        }
                        .buttonStyle(.link)
                        .font(.system(size: 12))
                    }
                }

                Divider()

                Label("完全离线：不联网、不登录、不上传", systemImage: "lock.shield")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
            .padding(16)
        }
        .frame(width: 380, height: 470)
    }
}
