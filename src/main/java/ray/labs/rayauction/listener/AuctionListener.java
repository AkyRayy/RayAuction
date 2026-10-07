package ray.labs.rayauction.listener;

import java.util.function.Supplier;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ray.labs.rayauction.gui.GuiService;
import ray.labs.rayauction.gui.holder.AnvilInputHolder;
import ray.labs.rayauction.gui.holder.AuctionSellHolder;
import ray.labs.rayauction.gui.holder.MenuHolder;

public final class AuctionListener implements Listener {

    private final Supplier<GuiService> gui;
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    public AuctionListener(Supplier<GuiService> gui) {
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean top = event.getClickedInventory() == event.getView().getTopInventory();
        if (!top) {
            return;
        }
        if (holder instanceof AnvilInputHolder && event.getSlot() != 2) {
            return;
        }
        holder.click(player, event.getSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof AnvilInputHolder)) {
            return;
        }
        ItemStack first = event.getInventory().getItem(0);
        ItemStack result = first == null ? null : first.clone();
        if (result != null) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                result.setItemMeta(meta);
            }
        }
        event.setResult(result);
        if (event.getViewers().isEmpty()) {
            return;
        }
        if (event.getViewers().get(0) instanceof Player player) {
            gui.get().prepareAnvilText(player, text(result));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (holder instanceof AnvilInputHolder) {
            gui.get().clearAnvilState(player);
            return;
        }
        if (holder instanceof AuctionSellHolder) {
            gui.get().dropSellSession(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof MenuHolder || event.getDestination().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private String text(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return "";
        }
        return plain.serialize(meta.displayName());
    }
}
