package mclite;

import java.util.Arrays;

public final class RegionLauncherApp {
    public static void main(String[] args) {
        RegionGate.lock();
        String[] merged = new String[args.length + 1];
        merged[0] = "--region=auto";
        System.arraycopy(args, 0, merged, 1, args.length);
        LauncherApp.main(merged);
    }
}
