package ray.labs.rayauction.platform;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface Scheduler {

    void runAsync(Runnable task);

    void runAsyncLater(Runnable task, long delayMillis);

    TaskHandle runAsyncTimer(Runnable task, long delayMillis, long periodMillis);

    void runAt(Location location, Runnable task);

    void runFor(Entity entity, Runnable task);

    void runGlobalTick(Runnable task);

    <T> CompletableFuture<T> supplyFor(Entity entity, Supplier<T> task);

    <T> CompletableFuture<T> supplyGlobal(Supplier<T> task);

    boolean isFolia();

    void cancelTasks();

    interface TaskHandle {

        void cancel();
    }
}
