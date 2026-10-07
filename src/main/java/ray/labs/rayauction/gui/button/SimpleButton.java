package ray.labs.rayauction.gui.button;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record SimpleButton(int slot, ItemStack icon, ClickHandler handler) implements Button {

    @Override
    public ItemStack icon(Player viewer) {
        return icon.clone();
    }
}
