package ray.labs.rayauction.platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class FoliaScheduler implements Scheduler {

    private final Plugin plugin;
    private final Server server;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
    }

    public static boolean isAvailable() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServerInitEvent");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    @Override
    public void runAsync(Runnable task) {
        server.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    @Override
    public void runAsyncLater(Runnable task, long delayMillis) {
        server.getAsyncScheduler()
                .runDelayed(plugin, ignored -> task.run(), Math.max(1L, delayMillis), TimeUnit.MILLISECONDS);
    }

    @Override
    public TaskHandle runAsyncTimer(Runnable task, long delayMillis, long periodMillis) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled = server.getAsyncScheduler()
                .runAtFixedRate(
                        plugin,
                        ignored -> task.run(),
                        Math.max(1L, delayMillis),
                        Math.max(1L, periodMillis),
                        TimeUnit.MILLISECONDS);
        return () -> scheduled.cancel();
    }

    @Override
    public void runAt(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            runGlobalTick(task);
            return;
        }
        server.getRegionScheduler()
                .run(plugin, location, ignored -> task.run());
    }

    @Override
    public void runFor(Entity entity, Runnable task) {
        if (entity == null) {
            runGlobalTick(task);
            return;
        }
        entity.getScheduler().run(plugin, ignored -> task.run(), task);
    }

    @Override
    public void runGlobalTick(Runnable task) {
        server.getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    @Override
    public <T> CompletableFuture<T> supplyFor(Entity entity, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try {
                future.complete(task.get());
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        };
        if (entity == null) {
            server.getGlobalRegionScheduler().run(plugin, ignored -> wrapped.run());
        } else {
            entity.getScheduler().run(plugin, ignored -> wrapped.run(), wrapped);
        }
        return future;
    }

    @Override
    public <T> CompletableFuture<T> supplyGlobal(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.getGlobalRegionScheduler().run(plugin, ignored -> {
            try {
                future.complete(task.get());
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    @Override
    public void cancelTasks() {
        try {
            server.getGlobalRegionScheduler().cancelTasks(plugin);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "cannot cancel Folia tasks", ex);
        }
    }
}
