package ray.labs.rayauction.platform;

import java.time.Clock;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ray.labs.rayauction.domain.AuctionItem;
import ray.labs.rayauction.domain.MailEntry;
import ray.labs.rayauction.domain.port.DeliveryPort;
import ray.labs.rayauction.domain.port.ItemGateway;
import ray.labs.rayauction.storage.MailRepository;

public final class PaperDeliveryPort implements DeliveryPort {

    private final Scheduler scheduler;
    private final ItemGateway items;
    private final MailRepository mail;
    private final boolean preferInventory;
    private final Clock clock;
    private final Logger logger;

    public PaperDeliveryPort(
            Scheduler scheduler,
            ItemGateway items,
            MailRepository mail,
            boolean preferInventory,
            Clock clock,
            Logger logger) {
        this.scheduler = scheduler;
        this.items = items;
        this.mail = mail;
        this.preferInventory = preferInventory;
        this.clock = clock;
        this.logger = logger;
    }

    @Override
    public boolean deliver(UUID playerId, AuctionItem item, MailEntry.MailReason reason, long auctionId) {
        if (preferInventory) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                boolean stored = scheduler.supplyFor(player, () -> items.give(playerId, item)).join();
                if (stored) {
                    return true;
                }
            }
        }
        store(playerId, item, reason, auctionId);
        return true;
    }

    private void store(UUID playerId, AuctionItem item, MailEntry.MailReason reason, long auctionId) {
        try {
            mail.insert(MailEntry.item(playerId, reason, item, auctionId, clock.instant()));
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "cannot store item of auction " + auctionId + " for " + playerId, ex);
        }
    }
}
