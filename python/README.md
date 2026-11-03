# MC Lite Python Bridge

可选模块，不包含在启动器 JAR 内。

## 安装

```bash
python3 -m pip install -r requirements-ui.txt
```

只写脚本不需要 PySide6；客户端只用标准库。

## 启动 Java Bridge

```bash
java -jar MCLite_v1.1.93.jar --py-bridge
```

启动后会生成：

```text
~/.mclite/bridge.json
```

包含 `url` 和 `token`。Bridge 只监听 `127.0.0.1`，所有 `/api/*` 请求都需要：

```text
X-MCLite-Token: <token>
```

## Python 客户端

```python
from mclite_bridge import BridgeClient

client = BridgeClient.from_file()
print(client.health())
print(client.versions(local_only=True))
print(client.search("Sodium", "mod", "26.1.2", "fabric"))
```

## PySide6 前端

```bash
python -m mclite_bridge.ui_pyside6 --jar /path/to/MCLite_v1.1.93.jar
```

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
| GET | `/api/versions?local=1` |
| GET | `/api/versions/{id}` |
| POST | `/api/versions/{id}/install` |
| GET | `/api/content/search` |
| GET | `/api/content/project/{id}` |
| GET | `/api/content/project/{id}/versions` |
| POST | `/api/content/install` |
| GET | `/api/tasks` |
| POST | `/api/tasks/{id}/cancel` |
| GET/PUT | `/api/config` |
| GET | `/api/datapacks?instance=ID` |
| POST | `/api/datapacks/sync?instance=ID` |
| GET | `/api/logs?tail=200` |
| POST | `/api/launch` |

## 限制

- 只支持本机回环地址
- 不提供任意文件读写
- 破坏性操作仍由 Java 侧执行，Python 只负责调用
- 未安装 Python/PySide6 时启动器完全不受影响
