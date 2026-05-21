package io.rcw.vibemine.ai.plugin.runtime.scheduler;

import io.rcw.vibemine.Vibemine;
import org.bukkit.Bukkit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JavaScript-safe scheduler facade for sync and async Bukkit tasks.
 */
public final class VibeScheduler {
    private final Context context;
    private final AtomicInteger pendingTasks;
    private final AtomicBoolean rootFinished;
    private final AtomicBoolean closed;

    public VibeScheduler() {
        this(null, null, null, null);
    }

    public VibeScheduler(Context context, AtomicInteger pendingTasks, AtomicBoolean rootFinished, AtomicBoolean closed) {
        this.context = context;
        this.pendingTasks = pendingTasks;
        this.rootFinished = rootFinished;
        this.closed = closed;
    }

    /**
     * Runs a task on the main server thread as soon as possible.
     */
    public VibeTask run(Value callback) {
        retain();
        return new VibeTask(Bukkit.getScheduler().runTask(Vibemine.getInstance(), once(callback)), this::release);
    }

    /**
     * Runs a task asynchronously as soon as possible.
     */
    public VibeTask async(Value callback) {
        retain();
        return new VibeTask(Bukkit.getScheduler().runTaskAsynchronously(Vibemine.getInstance(), once(callback)), this::release);
    }

    /**
     * Runs a task on the main server thread after {@code delayTicks} ticks.
     */
    public VibeTask later(long delayTicks, Value callback) {
        retain();
        return new VibeTask(Bukkit.getScheduler().runTaskLater(Vibemine.getInstance(), once(callback), delayTicks), this::release);
    }

    /**
     * Runs a task asynchronously after {@code delayTicks} ticks.
     */
    public VibeTask asyncLater(long delayTicks, Value callback) {
        retain();
        return new VibeTask(Bukkit.getScheduler().runTaskLaterAsynchronously(Vibemine.getInstance(), once(callback), delayTicks), this::release);
    }

    /**
     * Runs a repeating task on the main server thread.
     */
    public VibeTask repeat(long delayTicks, long periodTicks, Value callback) {
        retain();
        return new VibeTask(Bukkit.getScheduler().runTaskTimer(Vibemine.getInstance(), repeating(callback), delayTicks, periodTicks), this::release);
    }

    /**
     * Runs a repeating task asynchronously.
     */
    public VibeTask asyncRepeat(long delayTicks, long periodTicks, Value callback) {
        retain();
        return new VibeTask(Bukkit.getScheduler().runTaskTimerAsynchronously(Vibemine.getInstance(), repeating(callback), delayTicks, periodTicks), this::release);
    }

    private Runnable once(Value callback) {
        return () -> {
            try {
                execute(callback);
            } finally {
                release();
            }
        };
    }

    private Runnable repeating(Value callback) {
        return () -> execute(callback);
    }

    private void execute(Value callback) {
        if (context == null) {
            callback.executeVoid();
            return;
        }

        synchronized (context) {
            if (!closed.get()) callback.executeVoid();
        }
    }

    private void retain() {
        if (pendingTasks != null) pendingTasks.incrementAndGet();
    }

    private void release() {
        if (pendingTasks == null) return;
        if (pendingTasks.updateAndGet(value -> Math.max(0, value - 1)) == 0 && rootFinished.get()) closeContext();
    }

    public void rootFinished() {
        if (rootFinished == null) return;
        rootFinished.set(true);
        if (pendingTasks.get() == 0) closeContext();
    }

    private void closeContext() {
        synchronized (context) {
            if (closed.compareAndSet(false, true)) context.close(true);
        }
    }
}
