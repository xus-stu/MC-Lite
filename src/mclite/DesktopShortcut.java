package mclite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

public final class DesktopShortcut {
    private DesktopShortcut() {
    }

    public static Path updateIfInstalled(Path staged, String version) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return staged;
        }
        try {
            Path source = Path.of(DesktopShortcut.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(source)) {
                return staged;
            }
            Path installDir = source.getParent();
            if (installDir == null || !Files.isRegularFile(installDir.resolve("Uninstall.exe"))) {
                return staged;
            }
            Path target = installDir.resolve("MCLite_v" + version + ".jar");
            Path temp = Files.createTempFile(installDir, target.getFileName() + ".", ".tmp");
            try {
                Files.copy(staged, temp, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                try {
                    Files.deleteIfExists(temp);
                } catch (java.io.IOException ignored) {
                }
            }
            Path java = installDir.resolve("runtime/bin/javaw.exe");
            if (!Files.isRegularFile(java)) {
                // jar 已经装好；快捷方式暂时没改也不能退回缓存启动。
                Log.warn("安装目录缺少 javaw.exe，无法刷新快捷方式: " + java);
                return target;
            }
            String name = "MC Lite V" + version;
            String arguments = "-jar \"" + target + "\"";
            String script = "$ErrorActionPreference='SilentlyContinue';"
                    + "$ws=New-Object -ComObject WScript.Shell;"
                    + "Get-ChildItem ([Environment]::GetFolderPath('Desktop')) -Filter 'MC Lite*.lnk' | Remove-Item -Force;"
                    + "Get-ChildItem ([Environment]::GetFolderPath('Programs')) -Filter 'MC Lite*.lnk' | Remove-Item -Force;"
                    + "foreach($dir in @([Environment]::GetFolderPath('Desktop'),[Environment]::GetFolderPath('Programs'))){"
                    + "$s=$ws.CreateShortcut((Join-Path $dir '" + escape(name) + ".lnk'));"
                    + "$s.TargetPath='" + escape(java.toString()) + "';"
                    + "$s.Arguments='" + escape(arguments) + "';"
                    + "$s.WorkingDirectory='" + escape(installDir.toString()) + "';"
                    + "$s.Save()};";
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile",
                    "-ExecutionPolicy", "Bypass", "-Command", script)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.waitFor(12, TimeUnit.SECONDS);
            return target;
        } catch (Exception ex) {
            Log.warn("更新桌面快捷方式失败: " + ex.getMessage());
            return staged;
        }
    }

    private static String escape(String value) {
        return value.replace("'", "''");
    }
}
