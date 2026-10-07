package ray.labs.rayauction.domain.port;

import java.util.UUID;

import ray.labs.rayauction.domain.AuctionItem;
import ray.labs.rayauction.domain.MailEntry;

public interface DeliveryPort {

    boolean deliver(UUID playerId, AuctionItem item, MailEntry.MailReason reason, long auctionId);
}
