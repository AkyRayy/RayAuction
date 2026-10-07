package ray.labs.rayauction.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record CurrencyCatalog(List<Currency> currencies) {

    public CurrencyCatalog {
        currencies = List.copyOf(currencies);
    }

    private Map<String, Currency> index() {
        Map<String, Currency> index = new LinkedHashMap<>();
        for (Currency currency : currencies) {
            index.put(Money.normalizeId(currency.id()), currency);
        }
        return Collections.unmodifiableMap(index);
    }

    public Optional<Currency> byId(String id) {
        String key = Money.normalizeId(id);
        for (Currency currency : currencies) {
            if (Money.normalizeId(currency.id()).equals(key)) {
                return Optional.of(currency);
            }
        }
        return Optional.empty();
    }

    public Optional<Currency> byKind(String kind) {
        String key = Money.normalizeId(kind);
        for (Currency currency : currencies) {
            if (currencyKind(currency).equals(key)) {
                return Optional.of(currency);
            }
        }
        return Optional.empty();
    }

    public Optional<Currency> primary() {
        return currencies.isEmpty() ? Optional.empty() : Optional.of(currencies.get(0));
    }

    public boolean contains(Currency currency) {
        return byId(currency.id()).isPresent();
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>(currencies.size());
        for (Currency currency : currencies) {
            ids.add(currency.id());
        }
        return Collections.unmodifiableList(ids);
    }

    public static String currencyKind(Currency currency) {
        return switch (currency) {
            case Currency.Experience ignored -> Currency.Experience.ID;
            case Currency.Vault ignored -> Currency.Vault.ID;
            case Currency.CoinsEngine ignored -> "coinsengine";
            case Currency.PlayerPoints ignored -> Currency.PlayerPoints.ID;
        };
    }
}
