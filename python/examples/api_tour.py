# -*- coding: utf-8 -*-
# 逐行注释版 Bridge API 示例：把启动器能通过 HTTP 暴露的能力全部走一遍。
# 每一行代码后面的注释说明这一行在干什么、对应哪个 API。
# 运行前先启动 Bridge：java -jar MCLite_v1.3.5.jar --py-bridge
# Bridge 会在 ~/.mclite/bridge.json 写入 url 和临时 token。

import json  # 标准库：把返回的 JSON 打印得好看一点
from pathlib import Path  # 标准库：定位仓库里的 mclite_bridge 包
import sys  # 标准库：把 python/ 加入模块搜索路径
import sys  # 标准库：出错时用非零退出码结束脚本

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # 让脚本能直接找到 python/mclite_bridge
from mclite_bridge import BridgeClient  # 导入随启动器分发的 Python 客户端


def show(title, value):  # 定义一个打印函数，统一输出格式
    print("=" * 8, title, "=" * 8)  # 打印标题分隔线
    print(json.dumps(value, ensure_ascii=False, indent=2))  # 以中文可读的缩进 JSON 打印


def main():  # 主流程：所有 API 调用都写在这里
    client = BridgeClient.from_file()  # 从 ~/.mclite/bridge.json 读取 url 和 token 并连接
    show("health", client.health())  # GET /api/health：健康检查，无需 token
    show("info", client.info())  # GET /api/info：启动器版本、游戏目录、当前用户名
    show("capabilities", client.capabilities())  # GET /api/capabilities：当前服务端支持的能力列表
    print("api version:", client.api_version())  # 读取 health/info 里的 apiVersion 字段

    show("instances", client.instances())  # GET /api/instances：列出本地已安装实例
    show("versions(local)", client.versions(local_only=True))  # GET /api/versions?local=1：只看本地版本
    show("latest", client.latest_version())  # GET /api/versions/latest：官方最新版本
    versions = client.versions()  # GET /api/versions：完整版本清单（约 900 条）
    if versions:  # 有数据时再查单个版本，避免空列表报错
        show("version", client.version(versions[0]["id"]))  # GET /api/versions/{id}：单个版本详情
    if client.instances():  # 有实例时才查实例详情
        show("instance", client.instance(client.instances()[0]["id"]))  # GET /api/instances/{id}：实例详情

    show("sources", client.content_sources())  # GET /api/content/sources：Modrinth / CurseForge 是否可用
    results = client.search("Sodium", "mod", "26.1.2", "fabric")  # GET /api/content/search：搜索内容
    show("search", results[:3] if results else [])  # 只打印前三条，避免刷屏
    if results:  # 有结果时继续查项目和文件版本
        project_id = results[0]["id"]  # 记住第一个项目 id
        show("project", client.project(project_id))  # GET /api/content/project/{id}：项目详情
        show("project versions", client.project_versions(project_id, "26.1.2", "fabric")[:2])  # GET .../versions：文件版本

    show("accounts", client.accounts())  # GET /api/accounts：账户列表与当前选中项
    show("diagnostics", client.diagnostics())  # GET /api/diagnostics：最近一次崩溃的分类建议
    show("java", client.java())  # GET /api/java：已安装的 Java 运行时
    show("config", client.config())  # GET /api/config：当前启动器配置
    # client.update_config(memoryMb=6144)  # PUT /api/config：会真的改配置，默认注释
    # show("config(after)", client.config())  # 再读一次确认写入成功

    show("tasks", client.tasks())  # GET /api/tasks：当前/历史任务列表
    client.clear_tasks()  # POST /api/tasks/clear：清空已结束任务

    show("datapacks", client.datapacks())  # GET /api/datapacks：实例级全局数据包
    # 下面这行会把全局数据包写进存档，默认注释：
    # show("sync datapacks", client.sync_datapacks(client.instances()[0]["id"]))  # POST /api/datapacks/sync

    print("=" * 8, "logs (tail)", "=" * 8)  # 日志是纯文本，单独打印
    print(client.logs(30))  # GET /api/logs?tail=30：读取启动器日志最后 30 行

    # 下面这些调用会真的改世界或启动游戏，默认注释掉，需要时再打开：
    # client.install_version("26.1.2")  # POST /api/versions/{id}/install：下载/补齐版本
    # client.install_content(project_id, "26.1.2", "fabric")  # POST /api/content/install：安装内容
    # client.install_datapack("/path/to/pack.zip")  # POST /api/datapacks/install：安装数据包 ZIP
    # client.launch("26.1.2")  # POST /api/launch：启动游戏
    # client.cancel_task("task-id")  # POST /api/tasks/{id}/cancel：取消任务


if __name__ == "__main__":  # 只有直接运行脚本时才执行
    try:  # 捕获连接失败，给出可操作的提示
        main()  # 调用主流程
    except Exception as error:  # Bridge 未启动 / token 失效 / 端口被占用都会到这里
        print("API 示例运行失败：", error, file=sys.stderr)  # 打印错误到 stderr
        print("请先运行：java -jar MCLite_v1.3.5.jar --py-bridge", file=sys.stderr)  # 提示启动 Bridge
        sys.exit(1)  # 用非零退出码表示失败
