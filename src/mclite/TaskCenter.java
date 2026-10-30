package mclite;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TaskCenter {
    private final List<DownloadTask> tasks = new CopyOnWriteArrayList<>();

    public DownloadTask start(String title) {
        return start(title, "Mojang 官方源");
    }

    public DownloadTask start(String title, String source) {
        DownloadTask task = new DownloadTask(title, source);
        tasks.add(0, task);
        while (tasks.size() > 40) {
            tasks.remove(tasks.size() - 1);
        }
        return task;
    }

    public void update(DownloadTask task, TaskProgress progress) {
        if (task == null || progress == null
                || task.state() == DownloadTask.State.CANCELLED) {
            return;
        }
        task.progress(progress.done(), progress.total());
        task.speed(progress.bytesPerSecond(), progress.etaSeconds());
        task.stage(progress.stage());
    }

    public void finish(DownloadTask task) {
        if (task != null && task.state() == DownloadTask.State.RUNNING) {
            task.state(DownloadTask.State.DONE);
        }
    }

    public void fail(DownloadTask task, String message) {
        if (task != null && task.state() != DownloadTask.State.CANCELLED) {
            task.stage(message == null ? "失败" : message);
            task.state(DownloadTask.State.FAILED);
        }
    }

    public void cancel(DownloadTask task) {
        if (task != null && task.state() == DownloadTask.State.RUNNING) {
            task.state(DownloadTask.State.CANCELLED);
            task.stage("已取消");
        }
    }

    public List<DownloadTask> snapshot() {
        return new ArrayList<>(tasks);
    }

    public int running() {
        int count = 0;
        for (DownloadTask task : tasks) {
            if (task.state() == DownloadTask.State.RUNNING) {
                count++;
            }
        }
        return count;
    }

    public int clearFinished() {
        int before = tasks.size();
        tasks.removeIf(task -> task.state() != DownloadTask.State.RUNNING);
        return before - tasks.size();
    }
}
