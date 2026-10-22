package mclite;
import java.nio.file.*;
public class NeoLaunchTest { public static void main(String[] a)throws Exception{
 Config c=Config.load(); c.gameDir(testGameDir()); c.autoJvmTuning(false);
 var paths=new GamePaths(c.gameDir()); var entry=LocalVersions.scan(paths).stream().filter(e->e.id().contains("NeoForge")).findFirst().orElseThrow();
 var meta=LocalVersions.load(paths,entry.id()); Path java=Path.of(System.getProperty("java.home"),"bin","java");
 try { var cmd=GameLauncher.buildCommand(c,meta,entry,java,true); System.out.println("ok cmd="+cmd.size()); } catch(Exception e){ System.out.println("ERR "+e); }
 }
    static Path testGameDir() {
        return Path.of(System.getProperty("mclite.test.gameDir",
                Path.of(System.getProperty("user.home"), ".minecraft").toString()));
    }
}
