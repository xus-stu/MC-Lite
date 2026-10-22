package mclite;

public interface ProgressSink {
    void total(long totalBytes);

    default void addTotal(long bytes) {
    }

    void add(long bytes);

    void stage(String text);

    void indeterminate(boolean value);

    ProgressSink NOOP = new ProgressSink() {
        @Override
        public void total(long totalBytes) {
        }

        @Override
        public void add(long bytes) {
        }

        @Override
        public void stage(String text) {
        }

        @Override
        public void indeterminate(boolean value) {
        }
    };
}
