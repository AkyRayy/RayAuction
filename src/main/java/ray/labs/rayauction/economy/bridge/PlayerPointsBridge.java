package ray.labs.rayauction.economy.bridge;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class PlayerPointsBridge implements BalanceBridge {

    private static final String PLUGIN_CLASS = "org.black_ixx.playerpoints.PlayerPoints";
    private static final String API_CLASS = "org.black_ixx.playerpoints.PlayerPointsAPI";

    private final Object api;
    private final Method look;
    private final Method take;
    private final Method give;
    private final Logger logger;

    private PlayerPointsBridge(Object api, Method look, Method take, Method give, Logger logger) {
        this.api = api;
        this.look = look;
        this.take = take;
        this.give = give;
        this.logger = logger;
    }

    public static Optional<PlayerPointsBridge> create(Logger logger) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (plugin == null || !plugin.isEnabled()) {
            return Optional.empty();
        }
        Optional<Class<?>> pluginType = Reflection.type(PLUGIN_CLASS);
        Optional<Class<?>> apiType = Reflection.type(API_CLASS);
        if (pluginType.isEmpty() || apiType.isEmpty() || !pluginType.get().isInstance(plugin)) {
            return Optional.empty();
        }
        Optional<Method> apiAccessor = Reflection.byArity(pluginType.get(), "getAPI", 0);
        if (apiAccessor.isEmpty()) {
            return Optional.empty();
        }
        Object api = Reflection.invoke(logger, apiAccessor.get(), plugin).orElse(null);
        if (api == null) {
            return Optional.empty();
        }
        Optional<Method> look = Reflection.method(apiType.get(), "look", UUID.class);
        Optional<Method> take = Reflection.method(apiType.get(), "take", UUID.class, int.class);
        Optional<Method> give = Reflection.method(apiType.get(), "give", UUID.class, int.class);
        if (look.isEmpty() || take.isEmpty() || give.isEmpty()) {
            logger.warning("PlayerPoints API has an unexpected shape, integration disabled");
            return Optional.empty();
        }
        return Optional.of(new PlayerPointsBridge(api, look.get(), take.get(), give.get(), logger));
    }

    @Override
    public String name() {
        return "PlayerPoints";
    }

    @Override
    public boolean available() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public Optional<BigDecimal> getBalance(UUID playerId) {
        return Reflection.invoke(logger, look, api, playerId).map(Reflection::decimal);
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        return Reflection.bool(Reflection.invoke(logger, take, api, playerId, Reflection.whole(amount)).orElse(null));
    }

    @Override
    public boolean deposit(UUID playerId, BigDecimal amount) {
        return Reflection.bool(Reflection.invoke(logger, give, api, playerId, Reflection.whole(amount)).orElse(null));
    }
}
