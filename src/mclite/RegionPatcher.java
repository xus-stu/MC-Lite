package mclite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class RegionPatcher {
    private RegionPatcher() {
    }

    public static void makeUnrestricted(Path source, Path target) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".patch");
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
                            || name.equals("mclite/region.lock")) {
                        continue;
                    }
                    out.putNextEntry(new JarEntry(name));
                    if (!entry.isDirectory()) {
                        try (InputStream in = jar.getInputStream(entry)) {
                            in.transferTo(out);
                        }
                    }
                    out.closeEntry();
                }
            }
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
