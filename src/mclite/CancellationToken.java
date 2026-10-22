package mclite;

public final class CancellationToken {
    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
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
