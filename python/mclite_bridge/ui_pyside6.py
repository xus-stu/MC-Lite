"""Optional PySide6 frontend for MC Lite.

This is a separate frontend, not a plugin injected into the Swing window.
Edit this file freely if you want a different UI.
"""
from __future__ import annotations

import argparse
import sys
from typing import Any

from .client import BridgeClient, BridgeError, start_bridge

try:
    from PySide6 import QtCore, QtWidgets
    from PySide6.QtCore import Qt
except ImportError:
    QtWidgets = None


class MainWindow(QtWidgets.QMainWindow if QtWidgets else object):
    def __init__(self, client: BridgeClient) -> None:
        super().__init__()
        self.client = client
        self.process = None
        self.setWindowTitle("MC Lite · Python Bridge")
        self.resize(980, 680)

        tabs = QtWidgets.QTabWidget()
        tabs.addTab(self._versions_tab(), "版本")
        tabs.addTab(self._content_tab(), "内容")
        tabs.addTab(self._tasks_tab(), "任务")
        tabs.addTab(self._datapacks_tab(), "数据包")
        tabs.addTab(self._logs_tab(), "日志")
        tabs.addTab(self._accounts_tab(), "账户")
        tabs.addTab(self._diagnostics_tab(), "诊断")
        tabs.addTab(self._settings_tab(), "扩展设置")
        self.setCentralWidget(tabs)

        self.timer = QtCore.QTimer(self)
        self.timer.timeout.connect(self.refresh_tasks)
        self.timer.timeout.connect(self.refresh_logs)
        self.timer.start(1500)
        self.refresh_versions()
        self.refresh_tasks()
        self.refresh_accounts()
        self.refresh_diagnostics()
        self.refresh_schema()

    def _versions_tab(self):
        page = QtWidgets.QWidget()
        layout = QtWidgets.QVBoxLayout(page)
        bar = QtWidgets.QHBoxLayout()
        self.versions = QtWidgets.QListWidget()
        refresh = QtWidgets.QPushButton("刷新版本")
        launch = QtWidgets.QPushButton("启动选中版本")
        install = QtWidgets.QPushButton("安装选中版本")
        refresh.clicked.connect(self.refresh_versions)
        launch.clicked.connect(self.launch_version)
        install.clicked.connect(self.install_version)
        bar.addWidget(refresh)
        bar.addWidget(launch)
        bar.addWidget(install)
        bar.addStretch(1)
        layout.addLayout(bar)
        layout.addWidget(self.versions)
        return page

    def _content_tab(self):
        page = QtWidgets.QWidget()
        layout = QtWidgets.QVBoxLayout(page)
        bar = QtWidgets.QHBoxLayout()
        self.project_type = QtWidgets.QComboBox()
        self.project_type.addItems(["mod", "resourcepack", "modpack", "shader"])
        self.game_version = QtWidgets.QLineEdit("26.1.2")
        self.loader = QtWidgets.QLineEdit("fabric")
        self.search_text = QtWidgets.QLineEdit()
        self.search_text.setPlaceholderText("搜索模组 / 资源包 / 整合包…")
        search = QtWidgets.QPushButton("搜索")
        install = QtWidgets.QPushButton("安装选中项目")
        search.clicked.connect(self.search_content)
        install.clicked.connect(self.install_content)
        bar.addWidget(self.project_type)
        bar.addWidget(self.game_version)
        bar.addWidget(self.loader)
        bar.addWidget(self.search_text, 1)
        bar.addWidget(search)
        bar.addWidget(install)
        layout.addLayout(bar)
        self.projects = QtWidgets.QListWidget()
        layout.addWidget(self.projects)
        return page

    def _tasks_tab(self):
        page = QtWidgets.QWidget()
        layout = QtWidgets.QVBoxLayout(page)
        bar = QtWidgets.QHBoxLayout()
        refresh = QtWidgets.QPushButton("刷新")
        cancel = QtWidgets.QPushButton("取消选中任务")
        clear = QtWidgets.QPushButton("清除已完成")
        refresh.clicked.connect(self.refresh_tasks)
        cancel.clicked.connect(self.cancel_task)
        clear.clicked.connect(self.clear_tasks)
        bar.addWidget(refresh)
        bar.addWidget(cancel)
        bar.addWidget(clear)
        bar.addStretch(1)
        layout.addLayout(bar)
        self.tasks = QtWidgets.QListWidget()
        layout.addWidget(self.tasks)
        return page

    def _datapacks_tab(self):
        page = QtWidgets.QWidget()
        layout = QtWidgets.QVBoxLayout(page)
        bar = QtWidgets.QHBoxLayout()
        self.instance = QtWidgets.QLineEdit()
        self.instance.setPlaceholderText("实例 ID，例如 26.1.2；留空用默认目录")
        refresh = QtWidgets.QPushButton("刷新数据包")
        sync = QtWidgets.QPushButton("同步到所有存档")
        install = QtWidgets.QPushButton("安装 ZIP")
        remove = QtWidgets.QPushButton("移除选中")
        refresh.clicked.connect(self.refresh_datapacks)
        sync.clicked.connect(self.sync_datapacks)
        install.clicked.connect(self.install_datapack)
        remove.clicked.connect(self.remove_datapack)
        bar.addWidget(self.instance, 1)
        bar.addWidget(refresh)
        bar.addWidget(sync)
        bar.addWidget(install)
        bar.addWidget(remove)
        layout.addLayout(bar)
        self.datapacks = QtWidgets.QListWidget()
        layout.addWidget(self.datapacks)
        return page

    def _logs_tab(self):
        page = QtWidgets.QWidget()
        layout = QtWidgets.QVBoxLayout(page)
        self.logs = QtWidgets.QPlainTextEdit()
        self.logs.setReadOnly(True)
        layout.addWidget(self.logs)
        return page

    def _accounts_tab(self):
        page = QtWidgets.QWidget()
        layout = QtWidgets.QVBoxLayout(page)
        bar = QtWidgets.QHBoxLayout()
        refresh = QtWidgets.QPushButton("刷新账户")
        refresh.clicked.connect(self.refresh_accounts)
        bar.addWidget(refresh)
        bar.addStretch(1)
        layout.addLayout(bar)
        self.accounts_list = QtWidgets.QListWidget()
        layout.addWidget(self.accounts_list)
        return page

    def _diagnostics_tab(self):
        page = QtWidgets.QWidget()
        layout = QtWidgets.QVBoxLayout(page)
        bar = QtWidgets.QHBoxLayout()
        self.diag_instance = QtWidgets.QLineEdit()
        self.diag_instance.setPlaceholderText("实例 id（留空使用最近版本）")
        analyze = QtWidgets.QPushButton("分析崩溃")
        analyze.clicked.connect(self.refresh_diagnostics)
        bar.addWidget(self.diag_instance)
        bar.addWidget(analyze)
        layout.addLayout(bar)
        self.diag_text = QtWidgets.QPlainTextEdit()
        self.diag_text.setReadOnly(True)
        layout.addWidget(self.diag_text)
        return page

    def _settings_tab(self):
        page = QtWidgets.QWidget()
        layout = QtWidgets.QVBoxLayout(page)
        self.schema_host = QtWidgets.QWidget()
        self.schema_form = QtWidgets.QFormLayout(self.schema_host)
        scroll = QtWidgets.QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setWidget(self.schema_host)
        layout.addWidget(scroll, 1)
        bar = QtWidgets.QHBoxLayout()
        reload_button = QtWidgets.QPushButton("重新读取")
        reload_button.clicked.connect(self.refresh_schema)
        save_button = QtWidgets.QPushButton("保存到启动器")
        save_button.clicked.connect(self.save_schema)
        bar.addWidget(reload_button)
        bar.addWidget(save_button)
        bar.addStretch(1)
        layout.addLayout(bar)
        self.schema_widgets = {}
        return page

    def refresh_schema(self) -> None:
        try:
            fields = self.client.config_schema()
        except BridgeError:
            return
        while self.schema_form.rowCount():
            self.schema_form.removeRow(0)
        self.schema_widgets.clear()
        for field in fields:
            key = field.get("key", "")
            kind = field.get("type", "text")
            value = field.get("value")
            if kind == "bool":
                widget = QtWidgets.QCheckBox()
                widget.setChecked(bool(value))
            elif kind == "int":
                widget = QtWidgets.QSpinBox()
                widget.setRange(int(field.get("min", 0)), int(field.get("max", 999999)))
                widget.setValue(int(value or 0))
            else:
                widget = QtWidgets.QLineEdit("" if value is None else str(value))
            self.schema_widgets[key] = widget
            self.schema_form.addRow(field.get("label", key), widget)

    def save_schema(self) -> None:
        values = {}
        for key, widget in self.schema_widgets.items():
            if isinstance(widget, QtWidgets.QCheckBox):
                values[key] = widget.isChecked()
            elif isinstance(widget, QtWidgets.QSpinBox):
                values[key] = widget.value()
            else:
                values[key] = widget.text()
        try:
            self.client.update_config(**values)
            QtWidgets.QMessageBox.information(self, "MC Lite", "设置已保存到启动器")
        except BridgeError as error:
            self.fail(error)

    def refresh_accounts(self) -> None:
        try:
            accounts = self.client.accounts()
            self.accounts_list.clear()
            for item in accounts:
                mark = " · 当前" if item.get("selected") else ""
                self.accounts_list.addItem(f"{item.get('name', '')}{mark}  [{item.get('id', '')}]")
        except BridgeError:
            pass

    def refresh_diagnostics(self) -> None:
        try:
            data = self.client.diagnostics(self.diag_instance.text().strip())
            lines = [f"实例：{data.get('instance') or '（最近版本）'}",
                     f"报告：{data.get('report') or '未找到崩溃报告'}"]
            findings = data.get("findings") or []
            if not findings:
                lines.append("没有发现可分类的问题。")
            for finding in findings:
                lines.append(f"\n[{finding.get('kind')}] {finding.get('summary')}\n建议：{finding.get('action')}")
            self.diag_text.setPlainText("\n".join(lines))
        except BridgeError:
            pass

    def fail(self, error: Exception) -> None:
        QtWidgets.QMessageBox.warning(self, "MC Lite Bridge", str(error))

    def refresh_versions(self) -> None:
        try:
            self.versions.clear()
            for item in self.client.versions(local_only=True):
                text = f"{item['id']}  ·  {item.get('type', '')}"
                row = QtWidgets.QListWidgetItem(text)
                row.setData(Qt.UserRole, item["id"])
                self.versions.addItem(row)
        except BridgeError as exc:
            self.fail(exc)

    def launch_version(self) -> None:
        row = self.versions.currentItem()
        if row is None:
            return
        try:
            result = self.client.launch(row.data(Qt.UserRole))
            QtWidgets.QMessageBox.information(self, "已启动", f"PID {result['pid']}\n{result['log']}")
        except BridgeError as exc:
            self.fail(exc)

    def install_version(self) -> None:
        row = self.versions.currentItem()
        if row is None:
            return
        try:
            self.client.install_version(row.data(Qt.UserRole))
            QtWidgets.QMessageBox.information(self, "已加入任务", "安装任务已提交，请查看任务页。")
        except BridgeError as exc:
            self.fail(exc)

    def search_content(self) -> None:
        try:
            self.projects.clear()
            for item in self.client.search(self.search_text.text(), self.project_type.currentText(),
                                           self.game_version.text(), self.loader.text()):
                text = f"{item['title']}  ·  {item.get('author', '')}  ·  {item['id']}"
                row = QtWidgets.QListWidgetItem(text)
                row.setData(Qt.UserRole, item["id"])
                self.projects.addItem(row)
        except BridgeError as exc:
            self.fail(exc)

    def install_content(self) -> None:
        row = self.projects.currentItem()
        if row is None:
            return
        try:
            self.client.install_content(row.data(Qt.UserRole), self.game_version.text(),
                                        self.loader.text())
            QtWidgets.QMessageBox.information(self, "已加入任务", "安装任务已提交，请查看任务页。")
        except BridgeError as exc:
            self.fail(exc)

    def refresh_tasks(self) -> None:
        try:
            current = self.tasks.currentItem()
            selected = current.data(Qt.UserRole) if current else None
            self.tasks.clear()
            for task in self.client.tasks():
                percent = task.get("percent", -1)
                suffix = f" · {percent}%" if percent is not None and percent >= 0 else ""
                text = f"{task.get('title')}  ·  {task.get('state')}  ·  {task.get('stage', '')}{suffix}"
                row = QtWidgets.QListWidgetItem(text)
                row.setData(Qt.UserRole, task.get("id"))
                self.tasks.addItem(row)
                if task.get("id") == selected:
                    self.tasks.setCurrentItem(row)
        except BridgeError:
            pass

    def cancel_task(self) -> None:
        row = self.tasks.currentItem()
        if row is None:
            return
        try:
            self.client.cancel_task(row.data(Qt.UserRole))
            self.refresh_tasks()
        except BridgeError as exc:
            self.fail(exc)

    def refresh_datapacks(self) -> None:
        try:
            self.datapacks.clear()
            data = self.client.datapacks(self.instance.text())
            for name in data.get("packs", []):
                self.datapacks.addItem(name)
            self.datapacks.addItem(f"存档数量：{data.get('worlds', 0)}")
        except BridgeError as exc:
            self.fail(exc)

    def sync_datapacks(self) -> None:
        try:
            result = self.client.sync_datapacks(self.instance.text())
            QtWidgets.QMessageBox.information(self, "已同步", f"同步到 {result['worlds']} 个存档。")
            self.refresh_datapacks()
        except BridgeError as exc:
            self.fail(exc)

    def install_datapack(self) -> None:
        path, _ = QtWidgets.QFileDialog.getOpenFileName(self, "选择数据包", "", "ZIP (*.zip)")
        if not path:
            return
        try:
            result = self.client.install_datapack(path, self.instance.text())
            QtWidgets.QMessageBox.information(self, "已安装",
                                              f"{result['name']}，同步到 {result['worlds']} 个存档。")
            self.refresh_datapacks()
        except BridgeError as exc:
            self.fail(exc)

    def remove_datapack(self) -> None:
        row = self.datapacks.currentItem()
        if row is None or "存档数量" in row.text():
            return
        try:
            self.client.remove_datapack(row.text(), self.instance.text())
            self.refresh_datapacks()
        except BridgeError as exc:
            self.fail(exc)

    def clear_tasks(self) -> None:
        try:
            self.client.clear_tasks()
            self.refresh_tasks()
        except BridgeError as exc:
            self.fail(exc)

    def refresh_logs(self) -> None:
        try:
            text = self.client.logs(200)
            if text and text != self.logs.toPlainText():
                self.logs.setPlainText(text)
        except BridgeError:
            pass


def main(argv: list[str] | None = None) -> int:
    if QtWidgets is None:
        print("需要 PySide6：python3 -m pip install -r requirements-ui.txt", file=sys.stderr)
        return 2
    parser = argparse.ArgumentParser(description="MC Lite PySide6 frontend")
    parser.add_argument("--jar", help="MCLite jar 路径；提供后自动启动 Java Bridge")
    parser.add_argument("--java", default="java")
    parser.add_argument("--url", help="已经运行的 Bridge URL")
    parser.add_argument("--token", help="Bridge token")
    parser.add_argument("--home", help="MCLite home，默认 ~/.mclite")
    args = parser.parse_args(argv)

    process = None
    try:
        if args.jar:
            client, process = start_bridge(args.jar, args.java, args.home)
        elif args.url and args.token:
            client = BridgeClient(args.url, args.token)
        else:
            client = BridgeClient.from_file(args.home)
    except Exception as exc:
        print(f"无法连接 Bridge: {exc}", file=sys.stderr)
        return 3

    app = QtWidgets.QApplication(sys.argv[:1])
    window = MainWindow(client)
    window.process = process
    window.show()
    code = app.exec()
    if process is not None:
        process.terminate()
    return code


if __name__ == "__main__":
    raise SystemExit(main())
