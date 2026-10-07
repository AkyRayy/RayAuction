package ray.labs.rayauction.domain.port;

import java.util.UUID;

import ray.labs.rayauction.domain.AuctionItem;
import ray.labs.rayauction.domain.ItemCategory;

public interface ItemGateway {

    AuctionItem describe(byte[] serialized);

    byte[] serialize(AuctionItem item);

    boolean canStore(UUID playerId, AuctionItem item);

    boolean give(UUID playerId, AuctionItem item);

    ItemCategory categorize(String material);
}
