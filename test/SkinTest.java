package mclite;
import java.nio.file.*;
public class SkinTest { public static void main(String[] a) throws Exception { Config c=Config.load(); AccountStore.Data d=AccountStore.load(c.accountsDir()); String id=d.accounts().get(0).id(); SkinStore.save(c,id,Path.of("/tmp/test-skin.png")); System.out.println(SkinStore.load(c,id).getWidth(null)); System.out.println(SkinStore.skinFile(c,id)); } }
