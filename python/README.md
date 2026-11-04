# MC Lite Python Bridge

可选模块，不包含在启动器 JAR 内。

## 安装

```bash
python3 -m pip install -r requirements-ui.txt
```

只写脚本不需要 PySide6；客户端只用标准库。

## 启动 Java Bridge

```bash
java -jar MCLite_v1.3.12.jar --py-bridge
```

启动后会生成：

```text
~/.mclite/bridge.json
```

包含 `url` 和 `token`。Bridge 只监听 `127.0.0.1`，所有 `/api/*` 请求都需要：

```text
X-MCLite-Token: <token>
```

## 在 PyCharm 里使用

脚本被复制到 PyCharm 项目里时，PyCharm 找不到 `mclite_bridge` 是正常的——包不在项目里。两种解法：

1. **启动器安装（推荐）**：设置页点「安装 Python 模块…」，列表只显示 `~/PycharmProjects/*/.venv` 里的环境（如 `WelcomeScreen · Python 3.14`）；选中即安装，找不到就点「选择 PyCharm 项目文件夹…」。
2. **命令行安装**：`<你的 python> -m pip install -e /path/to/repo/python`。

## Python 客户端

```python
from mclite_bridge import BridgeClient

client = BridgeClient.from_file()
print(client.health())
print(client.versions(local_only=True))
print(client.search("Sodium", "mod", "26.1.2", "fabric"))
```

## 逐行注释的 API 示例

`python/examples/api_tour.py`：每一行都有中文注释，按顺序覆盖 health/info/capabilities/versions/instances/content/tasks/config/java/datapacks/logs，安装和启动调用默认注释掉。

```bash
java -jar MCLite_v1.3.12.jar --py-bridge
PYTHONPATH=python python3 python/examples/api_tour.py
```

## 用外部 UI 增加设置界面

Bridge 暴露 `GET /api/config/schema`，返回可编辑设置的 `key/label/type/min/max/value`；外部 UI（例如 PySide6）据此渲染控件，再用 `PUT /api/config` 写回，**不需要改启动器**。

```python
for field in client.config_schema():
    print(field["key"], field["type"], field["value"])
client.update_config(memoryMb=8192, autoMemory=False)
```

`ui_pyside6.py` 的「扩展设置」标签页就是按这个协议动态生成的。

## PySide6 前端

```bash
python3 run_ui.py --jar /path/to/MCLite_v1.3.12.jar
```

`run_ui.py` 会自动寻找安装了 PySide6 的 Python（优先 `python3.14` / `python3.13`）。

或连接已经运行的 Bridge：

```bash
python -m mclite_bridge.ui_pyside6 --url http://127.0.0.1:PORT --token TOKEN
```

PySide6 是一个独立前端，不会注入或修改 Swing 窗口；它通过 Bridge 使用启动器能力。

## API

| 方法 | 路径 |
|---|---|
| GET | `/api/health` |
| GET | `/api/info` |
| GET | `/api/capabilities` |
| GET | `/api/versions?local=1` |
| GET | `/api/versions/latest` |
| GET | `/api/versions/{id}` |
| POST | `/api/versions/{id}/install` |
| GET | `/api/instances` |
| GET | `/api/content/sources` |
| GET | `/api/content/search` |
| GET | `/api/content/project/{id}` |
| GET | `/api/content/project/{id}/versions` |
| POST | `/api/content/install` |
| GET | `/api/tasks` |
| GET | `/api/tasks/{id}` |
| POST | `/api/tasks/{id}/cancel` |
| POST | `/api/tasks/clear` |
| GET/PUT | `/api/config` |
| GET | `/api/java` |
| GET | `/api/datapacks?instance=ID` |
| POST | `/api/datapacks/sync?instance=ID` |
| POST | `/api/datapacks/install` |
| POST | `/api/datapacks/remove` |
| GET | `/api/logs?tail=200` |
| POST | `/api/launch` |

`/api/v1/...` 是同一组接口的版本化别名，`/api/...` 继续保留。

## 兼容规则

- 旧路径不删除，只新增可选字段和接口。
- Python 客户端已有函数保留原名和参数。
- 新功能只增加方法；需要破坏性变更时发布新的 `/api/v2`。
- `GET /api/health` 的 `apiVersion` 用来判断服务端能力。

## 限制

- 只支持本机回环地址
- 不提供任意文件读写
- 破坏性操作仍由 Java 侧执行，Python 只负责调用
- 未安装 Python/PySide6 时启动器完全不受影响
