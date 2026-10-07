package ray.labs.rayauction.domain;

import java.util.Objects;
import java.util.UUID;

public record PurchaseTicket(Auction auction, UUID buyerId, String buyerName, boolean admin) {

    public PurchaseTicket {
        Objects.requireNonNull(auction, "auction");
        Objects.requireNonNull(buyerId, "buyerId");
        buyerName = buyerName == null ? "" : buyerName;
    }

    public Currency currency() {
        return auction.currency();
    }

    public Money price() {
        return auction.price();
    }
}
