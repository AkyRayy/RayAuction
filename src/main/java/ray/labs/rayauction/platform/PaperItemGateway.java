package ray.labs.rayauction.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ray.labs.rayauction.domain.AuctionItem;
import ray.labs.rayauction.domain.ItemCategory;
import ray.labs.rayauction.domain.port.ItemGateway;
import ray.labs.rayauction.util.ItemSerializer;

public final class PaperItemGateway implements ItemGateway {

    private final MaterialClassifier classifier;
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    public PaperItemGateway(MaterialClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public AuctionItem describe(byte[] serialized) {
        ItemStack stack = ItemSerializer.fromBytes(serialized);
        Material material = stack.getType();
        ItemMeta meta = stack.getItemMeta();
        String display = "";
        String lore = "";
        if (meta != null) {
            Component displayName = meta.displayName();
            if (displayName != null) {
                display = plain.serialize(displayName);
            }
            List<Component> loreLines = meta.lore();
            if (loreLines != null && !loreLines.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                for (Component line : loreLines) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(plain.serialize(line));
                }
                lore = builder.toString();
            }
        }
        int[] container = containerSize(stack, meta);
        return new AuctionItem(
                serialized,
                material.name(),
                display,
                lore,
                classifier.classify(material).key(),
                stack.getAmount(),
                container[0] == 1,
                container[1]);
    }

    @Override
    public byte[] serialize(AuctionItem item) {
        return item.rawData();
    }

    @Override
    public boolean canStore(UUID playerId, AuctionItem item) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        ItemStack stack = ItemSerializer.fromBytes(item.rawData());
        int freeSlots = freeSlots(player.getInventory());
        int needed = slotsNeeded(stack);
        return freeSlots >= needed;
    }

    @Override
    public boolean give(UUID playerId, AuctionItem item) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        ItemStack stack = ItemSerializer.fromBytes(item.rawData());
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        return overflow.isEmpty();
    }

    @Override
    public ItemCategory categorize(String material) {
        Material resolved = Material.matchMaterial(material == null ? "" : material.toUpperCase(Locale.ROOT));
        return resolved == null ? ItemCategory.OTHER : classifier.classify(resolved);
    }

    public List<ItemStack> containerContents(AuctionItem item) {
        ItemStack stack = ItemSerializer.fromBytes(item.rawData());
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockMeta) || !blockMeta.hasBlockState()) {
            return List.of();
        }
        BlockState state = blockMeta.getBlockState();
        if (!(state instanceof org.bukkit.block.Container container)) {
            return List.of();
        }
        Inventory inventory = container.getInventory();
        ItemStack[] contents = inventory.getContents();
        List<ItemStack> result = new ArrayList<>(contents.length);
        for (ItemStack entry : contents) {
            result.add(entry == null ? null : entry.clone());
        }
        List<ItemStack> normalized = new ArrayList<>(result.size());
        for (ItemStack entry : result) {
            normalized.add(entry == null ? ItemStack.empty() : entry);
        }
        return List.copyOf(normalized);
    }

    public Map<String, String> tags(AuctionItem item) {
        ItemStack stack = ItemSerializer.fromBytes(item.rawData());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Map.of();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return new TagSnapshot(container).snapshot();
    }

    private int[] containerSize(ItemStack stack, ItemMeta meta) {
        Material material = stack.getType();
        String name = material.name();
        boolean shulker = name.endsWith("_SHULKER_BOX") || name.equals("SHULKER_BOX");
        boolean barrel = material == Material.BARREL;
        if (!shulker && !barrel) {
            return new int[] {0, 0};
        }
        if (meta instanceof BlockStateMeta blockMeta && blockMeta.hasBlockState()) {
            BlockState state = blockMeta.getBlockState();
            if (state instanceof org.bukkit.block.Container container) {
                return new int[] {1, container.getInventory().getSize()};
            }
        }
        return new int[] {0, 0};
    }

    private static int freeSlots(PlayerInventory inventory) {
        int free = 0;
        for (ItemStack entry : inventory.getStorageContents()) {
            if (entry == null || entry.getType().isAir()) {
                free++;
            }
        }
        return free;
    }

    private static int slotsNeeded(ItemStack stack) {
        int max = stack.getMaxStackSize();
        if (max <= 1) {
            return stack.getAmount();
        }
        return (stack.getAmount() + max - 1) / max;
    }

    private record TagSnapshot(PersistentDataContainer container) {


        Map<String, String> snapshot() {
            Map<String, String> values = new LinkedHashMap<>();
            for (var key : container.getKeys()) {
                String value = read(key);
                if (value != null) {
                    values.put(key.toString(), value);
                }
            }
            return Map.copyOf(values);
        }

        private String read(org.bukkit.NamespacedKey key) {
            for (PersistentDataType<?, ?> type : List.of(
                    PersistentDataType.STRING,
                    PersistentDataType.INTEGER,
                    PersistentDataType.LONG,
                    PersistentDataType.DOUBLE,
                    PersistentDataType.BOOLEAN,
                    PersistentDataType.BYTE,
                    PersistentDataType.SHORT,
                    PersistentDataType.FLOAT)) {
                if (!container.has(key, type)) {
                    continue;
                }
                Object value = container.get(key, type);
                return value == null ? null : String.valueOf(value);
            }
            return null;
        }
    }
}
