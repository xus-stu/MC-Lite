package mclite;
import java.nio.file.*;
import java.util.List;
public final class PackInstallTest {
 public static void main(String[] a) throws Exception {
  Log.init(Path.of("/tmp/mclite-pack-test.log"));
  Modrinth m=new Modrinth(ProgressSink.NOOP,new CancellationToken());
  List<Modrinth.Project> ps=m.search("Faithful","resourcepack","1.20.1",null);
  System.out.println(ps.size());
  if(!ps.isEmpty()){
   System.out.println(ps.get(0).title()+" "+ps.get(0).id());
   ContentInstaller.Result r=new ContentInstaller(Config.load(),ProgressSink.NOOP,new CancellationToken()).install(ps.get(0),"1.20.1",null);
   System.out.println(r.summary());
   try(var s=Files.list(Path.of(System.getProperty("mclite.gameDir")).resolve("resourcepacks"))){s.forEach(System.out::println);}
  }
 }
}
