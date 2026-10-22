package mclite;
import java.nio.file.*;
import java.util.List;
public class DepTest { public static void main(String[] a) throws Exception { Log.init(Path.of("/tmp/mclite-dep.log")); Config c=Config.load(); Modrinth m=new Modrinth(ProgressSink.NOOP,new CancellationToken()); List<Modrinth.Project> ps=m.search("Iris Shaders","mod","1.20.1","fabric"); System.out.println(ps.size()); if(ps.isEmpty())return; var p=ps.get(0); System.out.println(p.title()); var r=new ContentInstaller(c,ProgressSink.NOOP,new CancellationToken()).install(p,"1.20.1","fabric"); System.out.println(r.summary()); try(var s=Files.list(c.gameDir().resolve("mods"))){s.forEach(x->System.out.println(x.getFileName()));} } }
