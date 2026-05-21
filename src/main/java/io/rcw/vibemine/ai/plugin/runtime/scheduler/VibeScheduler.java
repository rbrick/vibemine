package io.rcw.vibemine.ai.plugin.runtime.scheduler;

import io.rcw.vibemine.Vibemine;
import org.bukkit.Bukkit;

/**
 * JavaScript-safe scheduler facade for sync and async Bukkit tasks.
 */
public final class VibeScheduler {
    /**
     * Runs a task on the main server thread as soon as possible.
     */
    public VibeTask run(Runnable runnable) {
        return new VibeTask(Bukkit.getScheduler().runTask(Vibemine.getInstance(), runnable));
    }

    /**
     * Runs a task asynchronously as soon as possible.
     */
    public VibeTask async(Runnable runnable) {
        return new VibeTask(Bukkit.getScheduler().runTaskAsynchronously(Vibemine.getInstance(), runnable));
    }

    /**
     * Runs a task on the main server thread after {@code delayTicks} ticks.
     */
    public VibeTask later(long delayTicks, Runnable runnable) {
        return new VibeTask(Bukkit.getScheduler().runTaskLater(Vibemine.getInstance(), runnable, delayTicks));
    }

    /**
     * Runs a task asynchronously after {@code delayTicks} ticks.
     */
    public VibeTask asyncLater(long delayTicks, Runnable runnable) {
        return new VibeTask(Bukkit.getScheduler().runTaskLaterAsynchronously(Vibemine.getInstance(), runnable, delayTicks));
    }

    /**
     * Runs a repeating task on the main server thread.
     */
    public VibeTask repeat(long delayTicks, long periodTicks, Runnable runnable) {
        return new VibeTask(Bukkit.getScheduler().runTaskTimer(Vibemine.getInstance(), runnable, delayTicks, periodTicks));
    }

    /**
     * Runs a repeating task asynchronously.
     */
    public VibeTask asyncRepeat(long delayTicks, long periodTicks, Runnable runnable) {
        return new VibeTask(Bukkit.getScheduler().runTaskTimerAsynchronously(Vibemine.getInstance(), runnable, delayTicks, periodTicks));
    }
}
