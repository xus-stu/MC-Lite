package mclite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small LRU + TTL cache for content search results.
 *
 * <p>Switching tabs or pressing 搜索 again within the TTL reuses the previous page instead of
 * hitting Modrinth/CurseForge again. Access happens from background workers and the EDT, so every
 * method is synchronized.</p>
 */
final class ContentCache {
    record Key(String source, String type, String query, String gameVersion, String loader,
               String sort, int page, boolean translate) {
    }

    record Result(List<Modrinth.Project> projects, Map<String, String> chineseTitles) {
    }

    private record Entry(Result result, long expiresAt) {
    }

    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;
    private static final int DEFAULT_MAX_ENTRIES = 40;

    private final long ttlMs;
    private final int maxEntries;
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    ContentCache() {
        this(DEFAULT_TTL_MS, DEFAULT_MAX_ENTRIES);
    }

    ContentCache(long ttlMs, int maxEntries) {
        this.ttlMs = Math.max(0, ttlMs);
        this.maxEntries = Math.max(1, maxEntries);
    }

    synchronized Result get(Key key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expiresAt()) {
            entries.remove(key);
            return null;
        }
        return entry.result();
    }

    synchronized void put(Key key, Result result) {
        if (result == null || result.projects() == null || result.projects().isEmpty()) {
            return;
        }
        entries.put(key, new Entry(result, System.currentTimeMillis() + ttlMs));
        while (entries.size() > maxEntries) {
            Key eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
        }
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized void clear() {
        entries.clear();
    }
}
