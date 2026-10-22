package mclite;
import java.nio.file.*;
public class LocalTest {
 public static void main(String[] a) throws Exception {
  Path root=Files.createTempDirectory("mclite-local");
  Path parent=root.resolve("versions/1.20.1"); Files.createDirectories(parent);
  Files.writeString(parent.resolve("1.20.1.json"), """
   {"id":"1.20.1","type":"release","mainClass":"net.minecraft.client.main.Main","assets":"5",
    "assetIndex":{"id":"5","url":"https://piston-meta.mojang.com/x.json","sha1":"0000000000000000000000000000000000000000","size":1,"totalSize":0},
    "downloads":{"client":{"url":"https://piston-data.mojang.com/x/client.jar","sha1":"0000000000000000000000000000000000000000","size":1}},
    "libraries":[{"name":"a:b:1","downloads":{"artifact":{"path":"a/b/1/b-1.jar","url":"https://libraries.minecraft.net/b.jar","sha1":"0000000000000000000000000000000000000000","size":1}}}],
    "arguments":{"jvm":["-cp","${classpath}"],"game":["--username","${auth_player_name}"]},
    "javaVersion":{"majorVersion":17}}
   """);
  Path child=root.resolve("versions/fabric-loader-test"); Files.createDirectories(child);
  Files.writeString(child.resolve("fabric-loader-test.json"), """
   {"id":"fabric-loader-test","inheritsFrom":"1.20.1","mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
    "libraries":[{"name":"c:d:2","downloads":{"artifact":{"path":"c/d/2/d-2.jar","url":"https://maven.fabricmc.net/d.jar","sha1":"0000000000000000000000000000000000000000","size":1}}}]}
   """);
  VersionMeta meta=LocalVersions.load(new GamePaths(root),"fabric-loader-test");
  System.out.println(meta.id()+" / "+meta.clientJarId()+" / "+meta.mainClass()+" / libs="+meta.libraries().size()+" / args="+meta.gameArgs().size());
  if(!meta.clientJarId().equals("1.20.1")||meta.libraries().size()!=2) throw new AssertionError();
  System.out.println(LocalVersions.scan(new GamePaths(root)));
 }
}
