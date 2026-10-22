package mclite;
import java.nio.file.*;
public class McScanTest { public static void main(String[] a)throws Exception{ var p=new GamePaths(testGameDir()); for(var e:LocalVersions.scan(p)){ System.out.println("["+e.id()+"] local="+e.local()+" type="+e.type()); try{var m=LocalVersions.load(p,e.id()); System.out.println("  main="+m.mainClass()+" jar="+m.clientJarId()+" libs="+m.libraries().size());}catch(Exception ex){System.out.println("  ERR "+ex);} } } 
    static Path testGameDir() {
        return Path.of(System.getProperty("mclite.test.gameDir",
                Path.of(System.getProperty("user.home"), ".minecraft").toString()));
    }
}
