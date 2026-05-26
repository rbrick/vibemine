package io.rcw.vibemine.ai.plugin.runtime.scheduler;

import org.bukkit.scheduler.BukkitTask;

/**
 * JavaScript-safe wrapper for a scheduled Bukkit task.
 */
public final class VibeTask {
    private final BukkitTask task;
    private final Runnable onCancel;

    /**
     * Creates a wrapper around a Bukkit task.
     */
    public VibeTask(BukkitTask task) {
        this(task, null);
    }

    /**
     * Creates a wrapper around a Bukkit task with a cancellation callback.
     */
    public VibeTask(BukkitTask task, Runnable onCancel) {
        this.task = task;
        this.onCancel = onCancel;
    }

    /**
     * Cancels this task.
     */
    public void cancel() {
        this.task.cancel();
        if (this.onCancel != null) this.onCancel.run();
    }

    /**
     * Returns whether this task has been cancelled.
     */
    public boolean isCancelled() { return this.task.isCancelled(); }

    /**
     * Returns the Bukkit task id.
     */
    public int getTaskId() { return this.task.getTaskId(); }

    /**
     * Returns the underlying Bukkit task.
     */
    BukkitTask unwrap() { return this.task; }
}
