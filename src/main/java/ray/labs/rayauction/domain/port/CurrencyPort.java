package ray.labs.rayauction.domain.port;

import ray.labs.rayauction.domain.Currency;

public interface CurrencyPort {

    String kind();

    boolean available(Currency currency);

    MoneyConnection open();
}
