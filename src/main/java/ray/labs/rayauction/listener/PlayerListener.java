package ray.labs.rayauction.listener;

import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ray.labs.rayauction.config.Messages;
import ray.labs.rayauction.config.PluginConfig;
import ray.labs.rayauction.domain.AuctionService;
import ray.labs.rayauction.gui.GuiService;
import ray.labs.rayauction.platform.Scheduler;

public final class PlayerListener implements Listener {

    private final Supplier<AuctionService> auctions;
    private final Supplier<GuiService> gui;
    private final Supplier<PluginConfig> config;
    private final Supplier<Messages> messages;
    private final Scheduler scheduler;
    private final Logger logger;

    public PlayerListener(
            Supplier<AuctionService> auctions,
            Supplier<GuiService> gui,
            Supplier<PluginConfig> config,
            Supplier<Messages> messages,
            Scheduler scheduler,
            Logger logger) {
        this.auctions = auctions;
        this.gui = gui;
        this.config = config;
        this.messages = messages;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PluginConfig.JoinConfig join = config.get().join();
        scheduler.runAsyncLater(() -> {
            try {
                AuctionService.JoinReport report = auctions.get().onJoin(player.getUniqueId(), join.mailPreviewLimit());
                if (report.isEmpty() || !join.notifyMail()) {
                    return;
                }
                scheduler.runFor(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (report.experienceCredited() > 0) {
                        player.sendMessage(messages.get().render(
                                "join.experience-credited",
                                Map.of("amount", Long.toString(report.experienceCredited()))));
                    }
                    if (report.mailDelivered() > 0) {
                        player.sendMessage(messages.get().render(
                                "join.mail-delivered", Map.of("count", Integer.toString(report.mailDelivered()))));
                    }
                    if (report.mailPending() > 0) {
                        player.sendMessage(messages.get().render(
                                "join.mail-pending", Map.of("count", Integer.toString(report.mailPending()))));
                    }
                });
            } catch (RuntimeException ex) {
                logger.log(Level.WARNING, "cannot process join data for " + player.getName(), ex);
            }
        }, join.delayTicks() * 50L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        gui.get().onQuit(event.getPlayer().getUniqueId());
    }
}
