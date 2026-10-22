package mclite;
import java.nio.file.*;
public class PresetTest { public static void main(String[] a) throws Exception { for(var p:ThemePreset.ALL){ Path f=Files.createTempFile("preset-",".css"); Files.writeString(f,p.css()); Theme.applyCss(f); System.out.println(p.name()+" bg="+Integer.toHexString(Theme.BG.getRGB())+" accent="+Integer.toHexString(Theme.ACCENT.getRGB())); Files.deleteIfExists(f);} } }
