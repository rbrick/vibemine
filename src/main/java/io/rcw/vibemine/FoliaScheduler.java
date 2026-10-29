package io.rcw.vibemine;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.TimeUnit;

/** Scheduler entry points that work on both Paper and Folia. */
public final class FoliaScheduler {
    private FoliaScheduler() {}

    public static ScheduledTask run(Runnable task) {
        return Bukkit.getGlobalRegionScheduler().run(Vibemine.getInstance(), ignored -> task.run());
    }

    public static ScheduledTask later(Runnable task, long ticks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(Vibemine.getInstance(), ignored -> task.run(), Math.max(1, ticks));
    }

    public static ScheduledTask repeat(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(Vibemine.getInstance(), ignored -> task.run(), Math.max(1, delayTicks), periodTicks);
    }

    public static ScheduledTask async(Runnable task) {
        return Bukkit.getAsyncScheduler().runNow(Vibemine.getInstance(), ignored -> task.run());
    }

    public static ScheduledTask asyncLater(Runnable task, long ticks) {
        return Bukkit.getAsyncScheduler().runDelayed(Vibemine.getInstance(), ignored -> task.run(), ticks * 50, TimeUnit.MILLISECONDS);
    }

    public static ScheduledTask asyncRepeat(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(Vibemine.getInstance(), ignored -> task.run(), Math.max(1, delayTicks) * 50, periodTicks * 50, TimeUnit.MILLISECONDS);
    }

    public static void entity(Entity entity, Runnable task) {
        entity.getScheduler().run(Vibemine.getInstance(), ignored -> task.run(), null);
    }

    public static void region(Location location, Runnable task) {
        Bukkit.getRegionScheduler().run(Vibemine.getInstance(), location, ignored -> task.run());
    }

    public static ScheduledTask entityRepeat(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(Vibemine.getInstance(), ignored -> task.run(), null, Math.max(1, delayTicks), periodTicks);
    }
}
