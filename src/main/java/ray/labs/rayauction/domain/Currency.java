package ray.labs.rayauction.domain;

import java.util.Locale;
import java.util.Optional;

public sealed interface Currency {

    String id();

    String displayName();

    String symbol();

    int scale();

    default boolean isExperience() {
        return this instanceof Experience;
    }

    record Experience(String displayName, String symbol) implements Currency {

        public static final String ID = "experience";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public int scale() {
            return 0;
        }
    }

    record Vault(String currencyId, String displayName, String symbol, int scale) implements Currency {

        public static final String ID = "vault";

        @Override
        public String id() {
            return currencyId.isBlank() ? ID : currencyId.toLowerCase(Locale.ROOT);
        }
    }

    record CoinsEngine(String currencyId, String displayName, String symbol, int scale) implements Currency {

        @Override
        public String id() {
            return "coinsengine:" + currencyId.toLowerCase(Locale.ROOT);
        }
    }

    record PlayerPoints(String displayName, String symbol) implements Currency {

        public static final String ID = "playerpoints";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public int scale() {
            return 0;
        }
    }

    static Optional<Currency> parse(String raw, CurrencyCatalog catalog) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return catalog.byId(value);
    }
}
