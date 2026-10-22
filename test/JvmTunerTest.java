package mclite;
public class JvmTunerTest { public static void main(String[] a) throws Exception { var t=JvmTuner.tune(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java"),21,2048); System.out.println(t.applied()+" "+t.reason()); System.out.println(t.args()); } }
