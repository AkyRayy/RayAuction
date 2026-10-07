package ray.labs.rayauction.platform;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

public final class PaperScheduler implements Scheduler {

    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.scheduler = plugin.getServer().getScheduler();
    }

    @Override
    public void runAsync(Runnable task) {
        scheduler.runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runAsyncLater(Runnable task, long delayMillis) {
        scheduler.runTaskLaterAsynchronously(plugin, task, Math.max(1L, millisToTicks(delayMillis)));
    }

    @Override
    public TaskHandle runAsyncTimer(Runnable task, long delayMillis, long periodMillis) {
        org.bukkit.scheduler.BukkitTask bukkitTask = scheduler.runTaskTimerAsynchronously(
                plugin, task, Math.max(1L, millisToTicks(delayMillis)), Math.max(1L, millisToTicks(periodMillis)));
        return bukkitTask::cancel;
    }

    @Override
    public void runAt(Location location, Runnable task) {
        scheduler.runTask(plugin, task);
    }

    @Override
    public void runFor(Entity entity, Runnable task) {
        scheduler.runTask(plugin, task);
    }

    @Override
    public void runGlobalTick(Runnable task) {
        scheduler.runTask(plugin, task);
    }

    @Override
    public <T> CompletableFuture<T> supplyFor(Entity entity, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.runTask(plugin, () -> {
            try {
                future.complete(task.get());
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    @Override
    public <T> CompletableFuture<T> supplyGlobal(Supplier<T> task) {
        return supplyFor(null, task);
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public void cancelTasks() {
        scheduler.cancelTasks(plugin);
    }

    static long millisToTicks(long millis) {
        return Math.max(1L, Math.round(millis / 50.0d));
    }
}
