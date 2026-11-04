import SwiftUI
import AppKit

struct FlowMiniApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.openWindow) private var openWindow

    var body: some Scene {
        Window("FlowMini", id: "main") {
            ContentView(store: Store.shared)
                .frame(minWidth: 900, minHeight: 560)
        }
        .defaultSize(width: 1320, height: 860)
        .windowToolbarStyle(.unified(showsTitle: true))
        .commands { mainMenu }

        Window("设置", id: "settings") {
            SettingsView(store: Store.shared)
        }
        .windowResizability(.contentSize)
    }

    @CommandsBuilder
    private var mainMenu: some Commands {
        CommandGroup(replacing: .newItem) {
            Button("新建") { Store.shared.newDocument() }
                .keyboardShortcut("n", modifiers: .command)
            Button("打开…") { Store.shared.openDocument() }
                .keyboardShortcut("o", modifiers: .command)
            Divider()
            Menu("打开示例") {
                ForEach(Array(Samples.all.enumerated()), id: \.offset) { _, sample in
                    Button(sample.title) { Store.shared.replace(with: sample.text) }
                }
            }
        }

        CommandGroup(replacing: .saveItem) {
            Button("保存") { Store.shared.save() }
                .keyboardShortcut("s", modifiers: .command)
            Button("另存为…") { Store.shared.saveAs() }
                .keyboardShortcut("s", modifiers: [.command, .shift])
            Divider()
            Button("导出 PNG…") { Store.shared.exportPNG() }
                .keyboardShortcut("e", modifiers: .command)
            Button("导出 PDF…") { Store.shared.exportPDF() }
            Button("导出 SVG…") { Store.shared.exportSVG() }
        }

        CommandGroup(replacing: .appSettings) {
            Button("设置…") { openWindow(id: "settings") }
                .keyboardShortcut(",", modifiers: .command)
        }

        CommandGroup(after: .toolbar) {
            Divider()
            Button("适应窗口") { Store.shared.send(.fit) }
                .keyboardShortcut("0", modifiers: .command)
            Button("放大") { Store.shared.send(.zoomIn) }
                .keyboardShortcut("=", modifiers: .command)
            Button("缩小") { Store.shared.send(.zoomOut) }
                .keyboardShortcut("-", modifiers: .command)
            Divider()
            Button("显示 / 隐藏文字面板") {
                withAnimation(.easeInOut(duration: 0.18)) { Store.shared.showEditor.toggle() }
            }
            .keyboardShortcut("e", modifiers: [.command, .option])
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)

    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        true
    }

    func applicationWillTerminate(_ notification: Notification) {
        Store.shared.persistNow()
    }

    func applicationSupportsSecureRestorableState(_ app: NSApplication) -> Bool {
        true
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        guard let url = urls.first else { return }
        Store.shared.load(url: url)
    }
}
