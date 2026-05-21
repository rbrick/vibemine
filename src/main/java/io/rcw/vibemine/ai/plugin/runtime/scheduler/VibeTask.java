package io.rcw.vibemine.ai.plugin.runtime.scheduler;

import org.bukkit.scheduler.BukkitTask;

/**
 * JavaScript-safe wrapper for a scheduled Bukkit task.
 */
public final class VibeTask {
    private final BukkitTask task;

    /**
     * Creates a wrapper around a Bukkit task.
     */
    public VibeTask(BukkitTask task) {
        this.task = task;
    }

    /**
     * Cancels this task.
     */
    public void cancel() { this.task.cancel(); }

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
    public BukkitTask unwrap() { return this.task; }
}
