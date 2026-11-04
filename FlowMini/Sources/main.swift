import Foundation
import AppKit

// 命令行入口：--render 用于批量导出 / 测试，其余情况启动图形界面。

let cliArguments = CommandLine.arguments

if cliArguments.count > 1, CLI.handles(cliArguments[1]) {
    CLI.run(cliArguments)
    exit(0)
}

FlowMiniApp.main()
