package mclite;
import java.nio.file.*;
public class ConfigCssTest { public static void main(String[] a) throws Exception {
 Config c=Config.load(); c.isolateVersions(true); c.autoMemory(true);
 System.out.println("instance="+c.effectiveGameDir("1.20.1"));
 System.out.println("memory="+c.effectiveMemoryMb());
 Path f=Files.createTempFile("mclite-ui-",".css");
 Files.writeString(f,"background: #010203\ncolor: #AABBCC\nfont-size: 15\n");
 Theme.applyCss(f);
 System.out.println("bg="+Integer.toHexString(Theme.BG.getRGB())+" text="+Integer.toHexString(Theme.TEXT.getRGB())+" font="+Theme.BODY.getSize());
 } }
