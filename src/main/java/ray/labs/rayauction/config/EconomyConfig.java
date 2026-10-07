package ray.labs.rayauction.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.CurrencyCatalog;

public record EconomyConfig(List<CurrencySettings> currencies, String defaultCurrency, String taxAccountName) {

    public EconomyConfig {
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
        defaultCurrency = defaultCurrency == null ? "" : defaultCurrency.trim();
        taxAccountName = taxAccountName == null ? "" : taxAccountName.trim();
    }

    public static EconomyConfig from(YamlNode node) {
        List<CurrencySettings> settings = new ArrayList<>();
        for (YamlNode entry : node.nodeList("currencies")) {
            String kind = entry.string("type", "");
            if (kind.isBlank() || !entry.bool("enabled", true)) {
                continue;
            }
            settings.add(new CurrencySettings(
                    kind,
                    entry.string("id", ""),
                    entry.string("display-name", kind),
                    entry.string("symbol", kind),
                    entry.integer("scale", kind.equals(Currency.PlayerPoints.ID) || kind.equals(Currency.Experience.ID) ? 0 : 2),
                    true));
        }
        return new EconomyConfig(settings, node.string("default-currency", ""), node.string("tax-account", ""));
    }

    public CurrencyCatalog catalog() {
        List<Currency> currencies = new ArrayList<>();
        for (CurrencySettings settings : this.currencies) {
            settings.toCurrency().ifPresent(currencies::add);
        }
        if (currencies.isEmpty()) {
            currencies.add(new Currency.Experience("Опыт", "XP"));
        }
        return new CurrencyCatalog(currencies);
    }

    public Optional<String> defaultCurrencyId() {
        if (defaultCurrency.isBlank()) {
            return catalog().primary().map(Currency::id);
        }
        CurrencyCatalog catalog = catalog();
        Optional<Currency> byId = catalog.byId(defaultCurrency);
        if (byId.isPresent()) {
            return byId.map(Currency::id);
        }
        return catalog.byKind(defaultCurrency).map(Currency::id);
    }
}
