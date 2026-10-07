package ray.labs.rayauction.platform;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Tag;
import ray.labs.rayauction.domain.ItemCategory;

public final class MaterialClassifier {

    private static final Set<Material> RARE = EnumSet.noneOf(Material.class);

    static {
        addRare(
                "NETHER_STAR",
                "BEACON",
                "DRAGON_EGG",
                "ELYTRA",
                "ENCHANTED_GOLDEN_APPLE",
                "TOTEM_OF_UNDYING",
                "HEART_OF_THE_SEA",
                "DRAGON_HEAD",
                "SPAWNER",
                "TRIAL_KEY",
                "OMINOUS_TRIAL_KEY",
                "HEAVY_CORE",
                "WIND_CHARGE",
                "ENCHANTED_BOOK");
    }

    private final boolean tagsAvailable;

    public MaterialClassifier() {
        this.tagsAvailable = probe();
    }

    public ItemCategory classify(Material material) {
        if (material == null || material.isAir()) {
            return ItemCategory.OTHER;
        }
        if (RARE.contains(material)) {
            return ItemCategory.RARE;
        }
        if (tagsAvailable && Tag.ITEMS_SWORDS.isTagged(material)) {
            return ItemCategory.WEAPONS;
        }
        String name = material.name().toUpperCase(Locale.ROOT);
        if (isWeapon(name, material)) {
            return ItemCategory.WEAPONS;
        }
        if (tagsAvailable && Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(material)) {
            return ItemCategory.ARMOR;
        }
        if (isArmor(name)) {
            return ItemCategory.ARMOR;
        }
        if (material.isEdible()) {
            return ItemCategory.FOOD;
        }
        if (material.isBlock()) {
            return ItemCategory.BLOCKS;
        }
        return ItemCategory.OTHER;
    }

    private static boolean isWeapon(String name, Material material) {
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.equals("TRIDENT")
                || name.equals("BOW")
                || name.equals("CROSSBOW")
                || name.equals("MACE")
                || name.equals("SHIELD")
                || name.equals("TIPPED_ARROW")
                || name.equals("SPECTRAL_ARROW")
                || material == Material.ARROW;
    }

    private static boolean isArmor(String name) {
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || name.equals("TURTLE_HELMET")
                || name.equals("WOLF_ARMOR");
    }

    private static void addRare(String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                RARE.add(material);
            }
        }
    }

    private static boolean probe() {
        try {
            return Tag.ITEMS_SWORDS != null;
        } catch (Throwable error) {
            return false;
        }
    }
}
