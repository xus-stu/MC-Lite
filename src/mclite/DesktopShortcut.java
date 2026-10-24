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
            Files.copy(staged, target, StandardCopyOption.REPLACE_EXISTING);
            Path java = installDir.resolve("runtime/bin/javaw.exe");
            if (!Files.isRegularFile(java)) {
                return staged;
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
