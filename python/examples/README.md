# Bridge API 示例

`api_tour.py` 是逐行注释的 API 速查脚本：每一行代码后面都写了它调用的是哪个接口、参数是什么。

## 运行

```bash
# 终端 1：启动 Bridge
java -jar MCLite_v1.3.2.jar --py-bridge

# 终端 2：运行示例
PYTHONPATH=python python3 python/examples/api_tour.py
```

依赖只有标准库和随包的 `mclite_bridge`，不需要 PySide6。

脚本默认不会安装或启动任何东西；会改世界/启动游戏的调用都在末尾注释掉了，需要时自己取消注释。
