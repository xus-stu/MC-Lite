# Python Bridge 契约

## 版本

- 当前 API：`1.0`
- 稳定前缀：`/api`
- 版本化别名：`/api/v1`
- `GET /api/health` 返回 `apiVersion` 和 `apiPrefix`

## 兼容规则

1. 不删除已有路径。
2. 不修改已有 JSON 字段的类型和含义，只允许新增字段。
3. Python 客户端已有函数保留名称和参数；新能力通过新函数提供。
4. 破坏性变更必须新增 `/api/v2`，并至少保留一个版本的 `/api` 兼容。
5. 服务端升级不能要求旧 Python 客户端同步升级。

## 鉴权

- 只监听 `127.0.0.1`
- 除 `/api/health` 外需要 `X-MCLite-Token`
- token 在 `~/.mclite/bridge.json`

## 接口

见 `python/README.md` 的 API 表。能力列表可运行时查询：

```text
GET /api/capabilities
```
