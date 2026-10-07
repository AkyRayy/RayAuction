package ray.labs.rayauction.gui;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ray.labs.rayauction.config.GuiConfig;
import ray.labs.rayauction.config.Messages;
import ray.labs.rayauction.domain.AuctionItem;
import ray.labs.rayauction.gui.holder.ShulkerPreviewHolder;
import ray.labs.rayauction.platform.PaperItemGateway;

public final class ShulkerPreviewService {

    private final PaperItemGateway items;
    private final Messages messages;
    private final GuiConfig gui;

    public ShulkerPreviewService(PaperItemGateway items, Messages messages, GuiConfig gui) {
        this.items = items;
        this.messages = messages;
        this.gui = gui;
    }

    public boolean isPreviewable(AuctionItem item) {
        return item.container() && item.containerSize() > 0;
    }

    public void open(Player player, AuctionItem item, long auctionId) {
        if (!isPreviewable(item)) {
            player.sendMessage(messages.render("gui.preview.not-container"));
            return;
        }
        List<ItemStack> contents = items.containerContents(item);
        int size = gui.menu("preview", "gui.preview.title", 54).size();
        ShulkerPreviewHolder holder = new ShulkerPreviewHolder(auctionId);
        Inventory inventory = Bukkit.createInventory(
                holder, size, messages.render("gui.preview.title", Map.of("item", displayName(item))));
        for (int slot = 0; slot < contents.size() && slot + 9 < size; slot++) {
            inventory.setItem(9 + slot, contents.get(slot));
        }
        GuiConfig.ButtonConfig button =
                gui.button("preview-back").orElseGet(() -> new GuiConfig.ButtonConfig(
                        "preview-back", "BARRIER", 0, "preview-back", List.of(), size - 5, "preview", false));
        int backSlot = Math.min(Math.max(button.slot(), 0), size - 1);
        inventory.setItem(
                backSlot,
                ItemFactory.icon(
                        button.material(),
                        button.customModelData(),
                        messages.render("gui.button.preview-back.name"),
                        messages.renderList("gui.button.preview-back.lore", Map.of()),
                        button.glow()));
        holder.bind(backSlot, (clicker, clickType) -> clicker.closeInventory());
        player.openInventory(inventory);
    }

    public static String displayName(AuctionItem item) {
        return item.displayName().isEmpty() ? item.material() : item.displayName();
    }

    public Map<String, String> placeholders(AuctionItem item) {
        return Map.of(
                "item", displayName(item),
                "material", item.material(),
                "amount", Integer.toString(item.amount()),
                "slots", Integer.toString(item.containerSize()));
    }
}
