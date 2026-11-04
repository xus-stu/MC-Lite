# -*- mode: python ; coding: utf-8 -*-


a = Analysis(
    ['diskbench_gui.py'],
    pathex=[],
    binaries=[],
    datas=[('diskbench.py', '.')],
    hiddenimports=[],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='磁盘测速',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=['app.icns'],
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='磁盘测速',
)
app = BUNDLE(
    coll,
    name='磁盘测速.app',
    icon='app.icns',
    bundle_identifier='com.xiaoxu.diskbench',
)
