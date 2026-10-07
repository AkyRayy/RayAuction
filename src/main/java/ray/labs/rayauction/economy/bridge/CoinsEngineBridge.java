package ray.labs.rayauction.economy.bridge;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class CoinsEngineBridge implements BalanceBridge {

    private static final String API_CLASS = "su.nightexpress.coinsengine.api.CoinsEngineAPI";

    private final String currencyId;
    private final Method getBalance;
    private final Method withdraw;
    private final Method give;
    private final boolean takesCurrency;
    private final Logger logger;

    private CoinsEngineBridge(
            String currencyId, Method getBalance, Method withdraw, Method give, boolean takesCurrency, Logger logger) {
        this.currencyId = currencyId;
        this.getBalance = getBalance;
        this.withdraw = withdraw;
        this.give = give;
        this.takesCurrency = takesCurrency;
        this.logger = logger;
    }

    public static Optional<CoinsEngineBridge> create(String currencyId, Logger logger) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CoinsEngine");
        if (plugin == null || !plugin.isEnabled()) {
            return Optional.empty();
        }
        Optional<Class<?>> type = Reflection.type(API_CLASS);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        Optional<Method> balance = Reflection.byArity(type.get(), "getBalance", 2);
        Optional<Method> withdraw = Reflection.byArity(type.get(), "withdraw", 3);
        Optional<Method> give = Reflection.byArity(type.get(), "give", 3);
        if (balance.isEmpty() || withdraw.isEmpty() || give.isEmpty()) {
            logger.warning("CoinsEngine API has an unexpected shape, integration disabled");
            return Optional.empty();
        }
        boolean takesCurrency = withdraw.get().getParameterCount() == 3
                && withdraw.get().getParameterTypes()[2] == String.class;
        return Optional.of(new CoinsEngineBridge(currencyId, balance.get(), withdraw.get(), give.get(), takesCurrency, logger));
    }

    @Override
    public String name() {
        return "CoinsEngine";
    }

    @Override
    public boolean available() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CoinsEngine");
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public Optional<BigDecimal> getBalance(UUID playerId) {
        return Reflection.invoke(logger, getBalance, null, playerId, currencyId).map(Reflection::decimal);
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        Object result = call(withdraw, playerId, amount);
        return Reflection.bool(result);
    }

    @Override
    public boolean deposit(UUID playerId, BigDecimal amount) {
        Object result = call(give, playerId, amount);
        return Reflection.bool(result);
    }

    private Object call(Method method, UUID playerId, BigDecimal amount) {
        if (takesCurrency) {
            return Reflection.invoke(logger, method, null, playerId, Reflection.fractional(amount), currencyId)
                    .orElse(null);
        }
        return Reflection.invoke(logger, method, null, playerId, Reflection.fractional(amount)).orElse(null);
    }
}
