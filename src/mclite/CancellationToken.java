package mclite;

public final class CancellationToken {
    private volatile boolean cancelled;
    private final java.util.concurrent.ConcurrentLinkedQueue<AutoCloseable> resources =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    public void cancel() {
        cancelled = true;
        AutoCloseable resource;
        while ((resource = resources.poll()) != null) {
            close(resource);
        }
    }

    /** 注册需要随取消一起关闭的网络流，避免阻塞读取让任务停不下来。 */
    public void register(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        if (cancelled) {
            close(resource);
            return;
        }
        resources.add(resource);
        if (cancelled && resources.remove(resource)) {
            close(resource);
        }
    }

    public void unregister(AutoCloseable resource) {
        if (resource != null) {
            resources.remove(resource);
        }
    }

    private static void close(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception ignored) {
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void check() {
        if (cancelled) {
            throw new CancelledException();
        }
    }

    public static final class CancelledException extends RuntimeException {
        public CancelledException() {
            super("操作已取消");
        }
    }
}
