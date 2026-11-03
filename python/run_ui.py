#!/usr/bin/env python3
"""Find a Python interpreter with PySide6 and start the optional frontend."""
from __future__ import annotations

import os
import shutil
import subprocess
import sys

CANDIDATES = [sys.executable, "python3.14", "python3.13", "python3.12", "python3"]


def main() -> int:
    extra = sys.argv[1:]
    seen = set()
    for candidate in CANDIDATES:
        executable = shutil.which(candidate)
        if not executable or executable in seen:
            continue
        seen.add(executable)
        check = subprocess.run([executable, "-c", "import PySide6"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if check.returncode == 0:
            os.execv(executable, [executable, "-m", "mclite_bridge.ui_pyside6", *extra])
    print("没有找到安装了 PySide6 的 Python。", file=sys.stderr)
    print("请运行：python3.14 -m pip install -r requirements-ui.txt", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
