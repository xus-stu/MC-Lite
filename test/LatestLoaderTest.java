package mclite;
public class LatestLoaderTest { public static void main(String[] a) throws Exception { System.setProperty("java.net.preferIPv6Addresses","true"); Config c=Config.load(); var l=new LoaderInstaller(c,ProgressSink.NOOP,new CancellationToken()); System.out.println("fabric="+l.latestVersion("fabric")); System.out.println("quilt="+l.latestVersion("quilt")); } }
