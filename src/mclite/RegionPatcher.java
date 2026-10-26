package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * 把地区版 jar 转成普通版内容：去掉 region.lock，加上 unrestricted 标记。
 *
 * <p>这不是反编译，也不会修改任何 class 文件；只是重新打包资源条目，
 * 让 {@link RegionGate} 在普通版里不启用地区限制。</p>
 */
public final class RegionPatcher {
    private RegionPatcher() {
    }

    public static void makeUnrestricted(Path source, Path target) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".patch");
        try {
            try (JarFile jar = new JarFile(source.toFile())) {
                Manifest manifest = jar.getManifest();
                if (manifest == null) {
                    manifest = new Manifest();
                }
                Attributes attributes = manifest.getMainAttributes();
                if (attributes.getValue(Attributes.Name.MANIFEST_VERSION) == null) {
                    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
                }
                attributes.put(Attributes.Name.MAIN_CLASS, "Main");
                try (JarOutputStream out = new JarOutputStream(
                        Files.newOutputStream(temp), manifest)) {
                    out.putNextEntry(new JarEntry("mclite/unrestricted"));
                    out.write(1);
                    out.closeEntry();
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.equals("META-INF/MANIFEST.MF")
                                || name.equals("mclite/region.lock")
                                || name.equals("mclite/unrestricted")
                                || isSignature(name)) {
                            continue;
                        }
                        JarEntry copy = new JarEntry(name);
                        if (entry.getTime() >= 0) {
                            copy.setTime(entry.getTime());
                        }
                        out.putNextEntry(copy);
                        if (!entry.isDirectory()) {
                            try (InputStream in = jar.getInputStream(entry)) {
                                in.transferTo(out);
                            }
                        }
                        out.closeEntry();
                    }
                }
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | UnsupportedOperationException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean isSignature(String name) {
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        String file = name.substring("META-INF/".length());
        if (file.contains("/")) {
            return false;
        }
        String upper = file.toUpperCase(java.util.Locale.ROOT);
        return upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA")
                || upper.endsWith(".EC")
                || upper.startsWith("SIG-");
    }
}
