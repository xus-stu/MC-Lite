package mclite;
public class NetIconTest { public static void main(String[] a) throws Exception { String u="https://cdn.modrinth.com/data/u6dRKJwZ/4a3f18ac0d096c9f8e9176984c44be4e58f94c89_96.webp"; byte[] b=new Net(ProgressSink.NOOP,new CancellationToken()).getUnverifiedBytes(u,"icon"); System.out.println(b.length); } }
