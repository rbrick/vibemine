package io.rcw.vibemine.ai.plugin.runtime.scheduler;

import io.rcw.vibemine.Vibemine;
import org.bukkit.Bukkit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Set<VibeTask> tasks = ConcurrentHashMap.newKeySet();

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
        return track(new VibeTask(Bukkit.getScheduler().runTask(Vibemine.getInstance(), once(callback)), this::release));
    }

    /**
     * Runs a task asynchronously as soon as possible.
     */
    public VibeTask async(Value callback) {
        retain();
        return track(new VibeTask(Bukkit.getScheduler().runTaskAsynchronously(Vibemine.getInstance(), once(callback)), this::release));
    }

    /**
     * Runs a task on the main server thread after {@code delayTicks} ticks.
     */
    public VibeTask later(long delayTicks, Value callback) {
        retain();
        return track(new VibeTask(Bukkit.getScheduler().runTaskLater(Vibemine.getInstance(), once(callback), delayTicks), this::release));
    }

    /**
     * JavaScript-friendly overload: scheduler.later(callback, delayTicks).
     */
    public VibeTask later(Value callback, long delayTicks) {
        return later(delayTicks, callback);
    }

    /**
     * Alias for generated JavaScript that uses Bukkit-style naming.
     */
    public VibeTask runLater(Value callback, long delayTicks) {
        return later(delayTicks, callback);
    }

    /**
     * Runs a task asynchronously after {@code delayTicks} ticks.
     */
    public VibeTask asyncLater(long delayTicks, Value callback) {
        retain();
        return track(new VibeTask(Bukkit.getScheduler().runTaskLaterAsynchronously(Vibemine.getInstance(), once(callback), delayTicks), this::release));
    }

    /**
     * JavaScript-friendly overload: scheduler.asyncLater(callback, delayTicks).
     */
    public VibeTask asyncLater(Value callback, long delayTicks) {
        return asyncLater(delayTicks, callback);
    }

    /**
     * Runs a repeating task on the main server thread.
     */
    public VibeTask repeat(long delayTicks, long periodTicks, Value callback) {
        retain();
        return track(new VibeTask(Bukkit.getScheduler().runTaskTimer(Vibemine.getInstance(), repeating(callback), delayTicks, periodTicks), this::release));
    }

    /**
     * JavaScript-friendly overload: scheduler.repeat(callback, delayTicks, periodTicks).
     */
    public VibeTask repeat(Value callback, long delayTicks, long periodTicks) {
        return repeat(delayTicks, periodTicks, callback);
    }

    /**
     * Alias for generated JavaScript that uses Bukkit-style naming.
     */
    public VibeTask runRepeating(Value callback, long delayTicks, long periodTicks) {
        return repeat(delayTicks, periodTicks, callback);
    }

    /**
     * Runs a repeating task asynchronously.
     */
    public VibeTask asyncRepeat(long delayTicks, long periodTicks, Value callback) {
        retain();
        return track(new VibeTask(Bukkit.getScheduler().runTaskTimerAsynchronously(Vibemine.getInstance(), repeating(callback), delayTicks, periodTicks), this::release));
    }

    /**
     * JavaScript-friendly overload: scheduler.asyncRepeat(callback, delayTicks, periodTicks).
     */
    public VibeTask asyncRepeat(Value callback, long delayTicks, long periodTicks) {
        return asyncRepeat(delayTicks, periodTicks, callback);
    }

    /**
     * Cancels a task returned by this scheduler. Accepts null for easier generated JS cleanup code.
     */
    public void cancelTask(VibeTask task) {
        if (task != null) task.cancel();
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

    private VibeTask track(VibeTask task) {
        tasks.add(task);
        return task;
    }

    /**
     * Cancels every task scheduled through this facade. Used when a vibed plugin is unloaded/reloaded.
     */
    public void cancelAll() {
        for (VibeTask task : Set.copyOf(tasks)) task.cancel();
        tasks.clear();
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
