import Foundation
import SwiftUI
import AppKit
import UniformTypeIdentifiers

@MainActor
final class Store: ObservableObject {

    static let shared = Store()
    static let flowType = UTType(exportedAs: "com.flowmini.flow", conformingTo: .plainText)

    // MARK: 文档

    @Published var text: String = "" {
        didSet {
            guard text != oldValue else { return }
            textDidChange()
        }
    }
    @Published var fileURL: URL? {
        didSet { persistSoon() }
    }
    @Published var isDirty = false

    // MARK: 视图设置

    @Published var themeID: String = "ink" {
        didSet {
            guard themeID != oldValue else { return }
            rebuildLayout()
            persistSoon()
        }
    }
    @Published var direction: FlowDirection = .lr {
        didSet {
            guard direction != oldValue else { return }
            rebuildLayout()
            persistSoon()
        }
    }
    @Published var edgeStyle: EdgeStyle = .orthogonal {
        didSet {
            guard edgeStyle != oldValue else { return }
            persistSoon()
        }
    }
    @Published var showEditor = true {
        didSet { persistSoon() }
    }
    @Published var editorWidth: CGFloat = 360 {
        didSet { persistSoon() }
    }
    @Published var appearance: AppearanceMode = .system {
        didSet { persistSoon() }
    }
    @Published var accent: AccentChoice = .blue {
        didSet { persistSoon() }
    }
    @Published var density: Density = .normal {
        didSet {
            guard density != oldValue else { return }
            rebuildLayout()
            persistSoon()
        }
    }

    var activeSkin: UISkin? { UISkin.matching(appearance: appearance, accent: accent) }

    var pageColor: Color { activeSkin?.page ?? Color(nsColor: .windowBackgroundColor) }
    var panelColor: Color { activeSkin?.panel ?? Color(nsColor: .textBackgroundColor) }
    var barColor: Color { activeSkin?.bar ?? Color(nsColor: .windowBackgroundColor) }

    func apply(skin: UISkin) {
        appearance = skin.appearance
        accent = skin.accent
        themeID = skin.themeID
    }

    func resetSettings() {
        appearance = .system
        accent = .blue
        density = .normal
        direction = .lr
        edgeStyle = .orthogonal
        themeID = "ink"
        showToast("已恢复默认外观")
    }

    // MARK: 解析结果

    @Published var graph = FlowGraph()
    @Published var layout = LayoutResult()
    @Published var problems: [String] = []
    @Published var zoomPercent = 100
    @Published var command = CanvasCommand()
    @Published var showHelp = false
    @Published var toast: String?

    var theme: Theme { Theme.named(themeID) }

    var fileName: String {
        fileURL?.lastPathComponent ?? "未命名.flow"
    }

    var suggestedName: String {
        fileURL?.deletingPathExtension().lastPathComponent ?? "流程图"
    }

    var splitDragStart: CGFloat?

    private var persistWork: DispatchWorkItem?
    private var toastWork: DispatchWorkItem?

    // MARK: 初始化

    private init() {
        let defaults = UserDefaults.standard
        if let saved = defaults.string(forKey: Key.text), !saved.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            text = saved
        } else {
            text = Samples.welcome
        }
        if let saved = defaults.string(forKey: Key.theme), Theme.presets.contains(where: { $0.id == saved }) {
            themeID = saved
        }
        if let raw = defaults.string(forKey: Key.direction), let value = FlowDirection(rawValue: raw) {
            direction = value
        }
        if let raw = defaults.string(forKey: Key.edgeStyle), let value = EdgeStyle(rawValue: raw) {
            edgeStyle = value
        }
        if defaults.object(forKey: Key.showEditor) != nil {
            showEditor = defaults.bool(forKey: Key.showEditor)
        }
        if defaults.object(forKey: Key.editorWidth) != nil {
            editorWidth = CGFloat(defaults.double(forKey: Key.editorWidth))
        }
        if let raw = defaults.string(forKey: Key.appearance), let value = AppearanceMode(rawValue: raw) {
            appearance = value
        }
        if let raw = defaults.string(forKey: Key.accent), let value = AccentChoice(rawValue: raw) {
            accent = value
        }
        if let raw = defaults.string(forKey: Key.density), let value = Density(rawValue: raw) {
            density = value
        }
        if let path = defaults.string(forKey: Key.path), FileManager.default.fileExists(atPath: path) {
            fileURL = URL(fileURLWithPath: path)
        }

        rebuild()
        isDirty = false
    }

    // MARK: 解析 / 布局

    private func textDidChange() {
        isDirty = true
        rebuild()
        persistSoon()
    }

    private func rebuild() {
        let result = FlowParser.parse(text)
        graph = result.graph
        problems = result.problems
        rebuildLayout()
    }

    private func rebuildLayout() {
        let currentTheme = theme
        let graphSnapshot = graph
        layout = FlowLayout.make(graph: graphSnapshot, direction: direction, options: density.options) { node in
            Metrics.nodeSize(node, theme: currentTheme)
        }
    }

    // MARK: 文件

    func newDocument() {
        text = Samples.starter
        fileURL = nil
        isDirty = false
        send(.fit)
        showToast("新建草稿")
    }

    func replace(with content: String) {
        text = content
        isDirty = true
        send(.fit)
    }

    func openDocument() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [Store.flowType, .plainText, .text]
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        panel.title = "打开流程图"
        if panel.runModal() == .OK, let url = panel.url {
            load(url: url)
        }
    }

    func load(url: URL) {
        do {
            let content = try String(contentsOf: url, encoding: .utf8)
            text = content
            fileURL = url
            isDirty = false
            send(.fit)
            showToast("已打开 \(url.lastPathComponent)")
            persistNow()
        } catch {
            alert("打不开这个文件", error.localizedDescription)
        }
    }

    func save() {
        if let url = fileURL {
            write(to: url)
        } else {
            saveAs()
        }
    }

    func saveAs() {
        let panel = NSSavePanel()
        panel.allowedContentTypes = [Store.flowType]
        panel.nameFieldStringValue = fileName
        panel.isExtensionHidden = false
        panel.title = "保存流程图"
        if panel.runModal() == .OK, let url = panel.url {
            write(to: url)
            fileURL = url
        }
    }

    private func write(to url: URL) {
        do {
            try text.write(to: url, atomically: true, encoding: .utf8)
            isDirty = false
            showToast("已保存")
        } catch {
            alert("保存失败", error.localizedDescription)
        }
    }

    // MARK: 导出

    func exportPNG() {
        guard layout.size.width > 1, layout.size.height > 1 else {
            showToast("画布还是空的，先写一行 a -> b")
            return
        }
        let panel = NSSavePanel()
        panel.allowedContentTypes = [.png]
        panel.nameFieldStringValue = suggestedName + ".png"
        panel.title = "导出 PNG"
        guard panel.runModal() == .OK, let url = panel.url else { return }

        var scale: CGFloat = 2
        while layout.size.width * scale > 9000 || layout.size.height * scale > 9000 {
            scale *= 0.85
        }
        do {
            try Exporter.writePNG(layout: layout, theme: theme, style: edgeStyle, scale: scale, to: url)
            showToast("已导出 PNG（\(Int(scale))x）")
        } catch {
            alert("导出失败", error.localizedDescription)
        }
    }

    func exportPDF() {
        guard layout.size.width > 1, layout.size.height > 1 else {
            showToast("画布还是空的，先写一行 a -> b")
            return
        }
        let panel = NSSavePanel()
        panel.allowedContentTypes = [.pdf]
        panel.nameFieldStringValue = suggestedName + ".pdf"
        panel.title = "导出 PDF"
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            try Exporter.writePDF(layout: layout, theme: theme, style: edgeStyle, to: url)
            showToast("已导出 PDF")
        } catch {
            alert("导出失败", error.localizedDescription)
        }
    }

    func exportSVG() {
        guard layout.size.width > 1, layout.size.height > 1 else {
            showToast("画布还是空的，先写一行 a -> b")
            return
        }
        let panel = NSSavePanel()
        panel.allowedContentTypes = [.svg]
        panel.nameFieldStringValue = suggestedName + ".svg"
        panel.title = "导出 SVG"
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            try Exporter.writeSVG(layout: layout, theme: theme, style: edgeStyle, to: url)
            showToast("已导出 SVG")
        } catch {
            alert("导出失败", error.localizedDescription)
        }
    }

    // MARK: 命令

    func send(_ kind: CanvasCommand.Kind) {
        command = CanvasCommand(token: command.token + 1, kind: kind)
    }

    // MARK: 提示

    func showToast(_ message: String) {
        toast = message
        toastWork?.cancel()
        let work = DispatchWorkItem { [weak self] in
            withAnimation(.easeOut(duration: 0.25)) { self?.toast = nil }
        }
        toastWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0, execute: work)
    }

    private func alert(_ title: String, _ message: String) {
        let alert = NSAlert()
        alert.messageText = title
        alert.informativeText = message
        alert.alertStyle = .warning
        alert.addButton(withTitle: "好")
        alert.runModal()
    }

    // MARK: 持久化

    func persistNow() {
        let defaults = UserDefaults.standard
        defaults.set(text, forKey: Key.text)
        defaults.set(themeID, forKey: Key.theme)
        defaults.set(direction.rawValue, forKey: Key.direction)
        defaults.set(edgeStyle.rawValue, forKey: Key.edgeStyle)
        defaults.set(showEditor, forKey: Key.showEditor)
        defaults.set(Double(editorWidth), forKey: Key.editorWidth)
        defaults.set(appearance.rawValue, forKey: Key.appearance)
        defaults.set(accent.rawValue, forKey: Key.accent)
        defaults.set(density.rawValue, forKey: Key.density)
        if let path = fileURL?.path {
            defaults.set(path, forKey: Key.path)
        } else {
            defaults.removeObject(forKey: Key.path)
        }
    }

    private func persistSoon() {
        persistWork?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.persistNow() }
        persistWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4, execute: work)
    }

    private enum Key {
        static let text = "flowmini.text"
        static let theme = "flowmini.theme"
        static let direction = "flowmini.direction"
        static let edgeStyle = "flowmini.edgeStyle"
        static let showEditor = "flowmini.showEditor"
        static let editorWidth = "flowmini.editorWidth"
        static let appearance = "flowmini.appearance"
        static let accent = "flowmini.accent"
        static let density = "flowmini.density"
        static let path = "flowmini.path"
    }
}
