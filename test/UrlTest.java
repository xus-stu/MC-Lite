package mclite;
public class UrlTest { public static void main(String[] a) throws Exception { Modrinth m=new Modrinth(ProgressSink.NOOP,new CancellationToken()); var p=m.search("Faithful","resourcepack","1.20.1",null).get(0); var vs=m.versions(p.id(),"1.20.1",null); System.out.println(p.title()+" "+p.id()); System.out.println(vs.get(0).primaryFile()); } }
