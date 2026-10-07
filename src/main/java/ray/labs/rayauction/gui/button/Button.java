package ray.labs.rayauction.gui.button;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public interface Button {

    int slot();

    ItemStack icon(Player viewer);

    ClickHandler handler();

    @FunctionalInterface
    interface ClickHandler {

        void click(Player player, ClickType clickType);
    }
}
