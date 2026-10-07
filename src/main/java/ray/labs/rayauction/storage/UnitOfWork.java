package ray.labs.rayauction.storage;

import java.util.function.Supplier;

public interface UnitOfWork extends AutoCloseable {

    <T> T call(Supplier<T> action);

    boolean run(Supplier<Boolean> action);

    @Override
    void close();
}
