package mclite;

import java.util.List;

public final class ContentTest {
    public static void main(String[] args) throws Exception {
        Log.init(java.nio.file.Path.of("/tmp/mclite-content-test.log"));
        System.out.println("MC百科:");
        List<McMod.Entry> entries = new McMod().search("物品管理器");
        for (McMod.Entry e : entries) {
            System.out.println("  " + e.id() + " | " + e.title() + " | EN=" + e.englishName()
                    + " | " + e.summary().substring(0, Math.min(80, e.summary().length())));
        }
        System.out.println("Modrinth:");
        List<Modrinth.Project> projects = new Modrinth(ProgressSink.NOOP, new CancellationToken())
                .search("Just Enough Items", "mod", "1.20.1", "fabric");
        for (Modrinth.Project p : projects) {
            System.out.println("  " + p.id() + " | " + p.title() + " | " + p.iconUrl());
        }
    }
}
