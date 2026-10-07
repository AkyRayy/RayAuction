package ray.labs.rayauction.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ray.labs.rayauction.domain.BlacklistPolicy;

public record BlacklistConfig(BlacklistPolicy policy, List<String> rawMaterials) {

    public static BlacklistConfig load(Path file) {
        YamlNode root = YamlNode.load(file);
        List<String> materials = root.stringList("materials", List.of());
        List<String> lore = root.stringList("lore", List.of());
        List<BlacklistPolicy.NbtRule> rules = new ArrayList<>();
        for (String key : root.stringList("nbt-keys", List.of())) {
            if (key == null || key.isBlank()) {
                continue;
            }
            rules.add(new BlacklistPolicy.NbtRule(key.trim(), ""));
        }
        BlacklistPolicy policy = new BlacklistPolicy(
                materials,
                lore,
                rules,
                root.string("bypass-permission", "rayauction.blacklist.bypass"),
                root.bool("block-shulker-boxes", false));
        return new BlacklistConfig(policy, materials);
    }
}
