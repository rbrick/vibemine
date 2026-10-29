package io.rcw.vibemine.ai.plugin.runtime.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** JavaScript-safe wrapper for a scheduled task. */
public final class VibeTask {
    private static final AtomicInteger IDS = new AtomicInteger();
    private final int taskId = IDS.incrementAndGet();
    private final ScheduledTask task;
    private final Runnable onCancel;
    private final AtomicBoolean cancelNotified = new AtomicBoolean(false);

    public VibeTask(ScheduledTask task) { this(task, null); }

    public VibeTask(ScheduledTask task, Runnable onCancel) {
        this.task = task;
        this.onCancel = onCancel;
    }

    public void cancel() {
        this.task.cancel();
        if (this.onCancel != null && this.cancelNotified.compareAndSet(false, true)) this.onCancel.run();
    }

    public boolean isCancelled() { return this.task.isCancelled(); }

    public int getTaskId() { return taskId; }

    ScheduledTask unwrap() { return this.task; }
}
