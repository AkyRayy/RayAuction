package ray.labs.rayauction.listener;

import java.util.function.Supplier;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ray.labs.rayauction.gui.GuiService;
import ray.labs.rayauction.platform.Scheduler;

public final class ChatListener implements Listener {

    private final Supplier<GuiService> gui;
    private final Scheduler scheduler;
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    public ChatListener(Supplier<GuiService> gui, Scheduler scheduler) {
        this.gui = gui;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!gui.get().isAwaitingSearch(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String query = plain.serialize(event.message());
        scheduler.runFor(player, () -> gui.get().submitSearch(player, query));
    }
}
