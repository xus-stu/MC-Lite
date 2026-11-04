# diskbench — 磁盘 / U 盘测速（图形界面 + 命令行）

一个单文件、零第三方依赖的磁盘测速工具：顺序读写 + 4K 随机读写，跑完自动生成图表。

## 图形界面（推荐）

双击 **`磁盘测速.command`**，或：

```bash
python3 diskbench_gui.py
```

界面里：左边选磁盘（U 盘 / 内置磁盘自动列出）、点分段按钮选时长（10 / 60 / 120 / 300 / 1000 秒），
点「开始测试」即可。右侧实时显示当前阶段、速度和每秒曲线；结束后自动弹出图表窗口，
可「打开图片」或「另存为」。图表和 JSON 默认存到 `results/`。

```
顺序写入 165 MB/s · 顺序读取 365 MB/s · 4K 随机读 1389 IOPS · 4K 随机写 342 IOPS
```

## 命令行用法

```bash
# 不带参数：列出所有可写磁盘，输入编号选择要测哪个
python3 diskbench.py

# 直接指定（路径含空格要加引号）
python3 diskbench.py "/Volumes/SANDISK USB"

# 长测：每项 120 秒 / 1000 秒
python3 diskbench.py "/Volumes/SANDISK USB" --duration 120
python3 diskbench.py "/Volumes/SANDISK USB" --duration 1000
```

交互选择的样子：

```
检测到以下可写磁盘：

  1) SANDISK USB            msdos    共   114.5 GiB  可用    99.6 GiB
  2) xiaoxu                 apfs     共   460.4 GiB  可用   287.9 GiB  内置磁盘
  0) 退出

请选择要测试的编号:
```

## 参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `target` | 交互选择 | 要测试的目录，如 `/Volumes/XXX`、`~/` |
| `--duration` | `60` | 顺序写、顺序读各持续多少秒（5–3600；120–1000 适合长测） |
| `--random-duration` | `10` | 4K 随机写、随机读各持续多少秒 |
| `--max-write-gib` | 自动 | 本次最多写多少 GiB，`0` = `min(50, 可用空间 12%)` |
| `--outdir` | 当前目录 | 图表和 JSON 的输出目录 |
| `--label` | 卷名 | 图表标题里的设备名 |
| `--keep` | 关 | 保留临时测试文件（默认测完立即删除） |

## 输出

在 `--outdir` 下生成两个文件：

- `diskbench-<名字>-<时间>.png` — 图表（和 README 顶部那张同样的版式）
- `diskbench-<名字>-<时间>.json` — 原始数据，可自己再画图

图表内容：5 张指标卡（顺序读 / 顺序写平均 / 顺序写稳态 / 4K 随机读 / 4K 随机写）+ 顺序写入的每秒曲线。

## 测速原理

- **顺序写入**：1 MiB 块连续写，`fsync` 落盘；写满 `--duration` 秒或到达写入上限就停。
- **顺序读取**：把刚写的数据**只读一遍**（不是循环读），避免系统把数据缓存到内存后把速度算高。
- **绕过缓存**：macOS 用 `fcntl(F_NOCACHE)` 关闭该文件的缓存；Linux 用 `posix_fadvise(DONTNEED)`。
- **4K 随机**：在刚写的最后一段数据上随机偏移做 4 KiB 读/写，反映小文件性能。
- **FAT32 安全**：按 2 GiB 分段写（避开单文件 4 GiB 上限），测试文件全部以 `.diskbench-` 开头，结束时删除。

## 注意事项

- **写入量**：`--duration 60` 在 165 MB/s 的 U 盘上约写 10 GB，在内置 NVMe 上可能几秒就撞到 `--max-write-gib` 上限。想减少写入量就调小这个值。
- **中途别拷文件**：测速时如果有别的程序在读写同一块盘（Finder 拷贝、Spotlight 索引、Time Machine），结果会明显偏低且抖动，曲线会呈锯齿状。
- **出图依赖**：有 Pillow 就直接出 PNG；没有则输出 SVG（也可用系统 `qlmanage` 转 PNG）。安装 Pillow：`python3 -m pip install --user pillow`。
- **U 盘的「真实速度」**：顺序曲线前几秒偏高是写入缓存，之后才是持续速度；卡上「稳态」值就是剔除首秒后的平均。

## 建议的测试时长

| 场景 | `--duration` |
|---|---|
| 快速看一眼 | 10 |
| 默认 / 日常 | 60 |
| 判断是否有缓存耗尽掉速 | 120–300 |
| 老化 / 稳定性 | 1000 |

## 打包成 .app

已生成 **`磁盘测速.app`**（约 37 MB，自带 Python 运行时，不依赖系统环境）：

- 双击直接运行，也可以拖进「应用程序」文件夹
- 打包版的测试结果保存在 `~/Library/Application Support/DiskBench/results`
- 应用已做临时签名（ad-hoc），本机运行无提示；拷给别人时对方首次打开需右键 →「打开」

重新打包（改代码后）：

```bash
python3 -m pip install --user pyinstaller        # 只需一次
python3 -m PyInstaller --noconfirm --windowed \
  --name "磁盘测速" --icon app.icns \
  --osx-bundle-identifier com.xiaoxu.diskbench \
  --add-data "diskbench.py:." diskbench_gui.py
```

图标源文件是 `icon-1024.png`，需要改图标时重新生成 `AppIcon.iconset` 后
`iconutil -c icns AppIcon.iconset -o app.icns`。
