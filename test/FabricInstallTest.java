package mclite;
import java.nio.file.*;
public class FabricInstallTest {
 public static void main(String[] a) throws Exception {
  Path root=Files.createTempDirectory("mclite-fabric");
  Path pv=root.resolve("versions/1.20.1"); Files.createDirectories(pv);
  Files.writeString(pv.resolve("1.20.1.json"), """
   {"id":"1.20.1","type":"release","mainClass":"net.minecraft.client.main.Main","assets":"5",
    "assetIndex":{"id":"5","url":"https://piston-meta.mojang.com/x.json","sha1":"0000000000000000000000000000000000000000","size":1,"totalSize":0},
    "downloads":{"client":{"url":"https://piston-data.mojang.com/x/client.jar","sha1":"0000000000000000000000000000000000000000","size":1}},
    "arguments":{"jvm":["-cp","${classpath}"],"game":["--username","${auth_player_name}"]},
    "javaVersion":{"majorVersion":17}}
   """);
  Config base=Config.load();
  Config cfg=base.copyForGameDir(root);
  JavaManager.JavaInfo java=new JavaManager(cfg,ProgressSink.NOOP,new CancellationToken()).detectAll().get(0);
  String id=new LoaderInstaller(cfg,ProgressSink.NOOP,new CancellationToken())
    .install("fabric","1.20.1","0.16.9",java);
  System.out.println("loader="+id);
  VersionMeta meta=LocalVersions.load(new GamePaths(root),id);
  System.out.println("main="+meta.mainClass()+" jar="+meta.clientJarId()+" libs="+meta.libraries().size());
  Path lp=root.resolve("libraries/net/fabricmc/fabric-loader/0.16.9/fabric-loader-0.16.9.jar");
  System.out.println("loaderJar="+Files.isRegularFile(lp));
 } }
