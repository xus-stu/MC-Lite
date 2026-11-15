package mclite;

/**
 * 自检入口。自检代码不在主 JAR 里（省体积），由 build.sh 单独打成
 * dist/MCLiteTests_vX.jar，运行时和主 JAR 一起放进 classpath。
 */
public final class SelfTestMain {
    private SelfTestMain() {
    }

    public static void main(String[] args) {
        System.exit(SelfTest.run(Config.load()));
    }
}
