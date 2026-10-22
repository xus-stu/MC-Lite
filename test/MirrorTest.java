package mclite;
import java.nio.file.*;
public class MirrorTest { public static void main(String[] a) throws Exception {
 Path t=Files.createTempDirectory("mclite-mirror").resolve("asset.bin");
 String hash="b62ca8ec10d07e6bf5ac8dae0c8c1d2e6a1e3356";
 Net net=new Net(ProgressSink.NOOP,new CancellationToken());
 long st=System.currentTimeMillis();
 net.downloadRacing("https://resources.download.minecraft.net/b6/"+hash, Mirror.asset(hash), t, hash, 9101, "test-asset", true);
 System.out.println("ok size="+Files.size(t)+" ms="+(System.currentTimeMillis()-st));
 } }
