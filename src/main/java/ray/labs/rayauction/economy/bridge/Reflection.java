package ray.labs.rayauction.economy.bridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

final class Reflection {

    private Reflection() {}

    static Optional<Class<?>> type(String name) {
        try {
            return Optional.of(Class.forName(name));
        } catch (ClassNotFoundException | LinkageError ex) {
            return Optional.empty();
        }
    }

    static Optional<Method> method(Class<?> type, String name, Class<?>... parameters) {
        try {
            Method method = type.getMethod(name, parameters);
            method.setAccessible(true);
            return Optional.of(method);
        } catch (NoSuchMethodException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    static Optional<Method> byArity(Class<?> type, String name, int parameters) {
        for (Method candidate : type.getMethods()) {
            if (candidate.getName().equals(name) && candidate.getParameterCount() == parameters) {
                candidate.setAccessible(true);
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    static Optional<Object> invoke(Logger logger, Method method, Object target, Object... args) {
        try {
            return Optional.ofNullable(method.invoke(target, args));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            logger.log(Level.WARNING, "integration call failed: " + method, ex);
            return Optional.empty();
        }
    }

    static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof CompletableFuture<?> future) {
            Object joined = future.join();
            return joined instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    static boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof CompletableFuture<?> future) {
            return Boolean.TRUE.equals(future.join());
        }
        return value != null;
    }

    static int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof CompletableFuture<?> future) {
            Object joined = future.join();
            return joined instanceof Number number ? number.intValue() : 0;
        }
        return 0;
    }

    static double fractional(BigDecimal value) {
        return value.doubleValue();
    }

    static int whole(BigDecimal value) {
        return value.setScale(0, java.math.RoundingMode.DOWN).intValue();
    }
}
