package ray.labs.rayauction.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record PluginConfig(
        int configVersion,
        DatabaseConfig database,
        EconomyConfig economy,
        AuctionConfig auction,
        MultiServerConfig multiServer,
        DiscordConfig discord,
        CacheConfig cache,
        JoinConfig join,
        PlaceholderConfig placeholder,
        CommandsConfig commands,
        Sounds sounds,
        String locale,
        boolean debug) {

    public static final int CURRENT_VERSION = 1;

    private static final List<String> BUNDLED_FILES =
            List.of("config.yml", "messages.yml", "blacklist.yml", "gui.yml");

    public record CacheConfig(int maximumSize, int sqlFallbackThreshold, int refreshSeconds) {

        public CacheConfig {
            maximumSize = Math.max(64, maximumSize);
            sqlFallbackThreshold = Math.max(0, sqlFallbackThreshold);
            refreshSeconds = Math.max(5, refreshSeconds);
        }

        static CacheConfig from(YamlNode node) {
            return new CacheConfig(
                    node.integer("maximum-size", 20_000),
                    node.integer("sql-fallback-threshold", 20_000),
                    node.integer("refresh-seconds", 60));
        }
    }

    public record JoinConfig(boolean notifyMail, boolean notifyExpired, int delayTicks, int mailPreviewLimit) {

        public JoinConfig {
            delayTicks = Math.max(0, delayTicks);
            mailPreviewLimit = Math.max(1, mailPreviewLimit);
        }

        static JoinConfig from(YamlNode node) {
            return new JoinConfig(
                    node.bool("notify-mail", true),
                    node.bool("notify-expired", true),
                    node.integer("delay-ticks", 40),
                    node.integer("mail-preview-limit", 5));
        }
    }

    public static void installDefaults(Path dataFolder, List<String> missing) {
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException ex) {
            throw new ConfigException("cannot create plugin data folder " + dataFolder, ex);
        }
        for (String name : BUNDLED_FILES) {
            Path target = dataFolder.resolve(name);
            if (Files.exists(target)) {
                continue;
            }
            InputStream stream = PluginConfig.class.getClassLoader().getResourceAsStream(name);
            if (stream == null) {
                throw new ConfigException("bundled default config is missing from the jar: " + name);
            }
            try (InputStream input = stream) {
                Files.copy(input, target);
                missing.add(name);
            } catch (IOException ex) {
                throw new ConfigException("cannot write default config " + name, ex);
            }
        }
    }

    public static PluginConfig load(Path dataFolder) {
        YamlNode root = YamlNode.load(dataFolder.resolve("config.yml"));
        int version = root.integer("config-version", 0);
        if (version > CURRENT_VERSION) {
            throw new ConfigException("config-version "
                    + version
                    + " is newer than this build supports ("
                    + CURRENT_VERSION
                    + "). Update RayAuction or restore a backup.");
        }
        PluginConfig config = new PluginConfig(
                CURRENT_VERSION,
                DatabaseConfig.from(root.section("database")),
                EconomyConfig.from(root.section("economy")),
                AuctionConfig.from(root.section("auction")),
                MultiServerConfig.from(root.section("multi-server")),
                DiscordConfig.from(root.section("discord")),
                CacheConfig.from(root.section("cache")),
                JoinConfig.from(root.section("join")),
                PlaceholderConfig.from(root.section("placeholders")),
                CommandsConfig.from(root.section("commands")),
                Sounds.from(root.section("sounds")),
                root.string("locale", "ru"),
                root.bool("debug", false));
        if (version != CURRENT_VERSION) {
            migrate(dataFolder, version);
        }
        return config;
    }

    private static void migrate(Path dataFolder, int fromVersion) {
        Path file = dataFolder.resolve("config.yml");
        Path backup = dataFolder.resolve("config.yml.v" + fromVersion + ".backup");
        try {
            if (!Files.exists(backup)) {
                Files.copy(file, backup);
            }
            List<String> lines = Files.readAllLines(file);
            List<String> updated = lines.stream()
                    .map(line -> line.startsWith("config-version:") ? "config-version: " + CURRENT_VERSION : line)
                    .toList();
            Files.write(file, updated);
        } catch (IOException ex) {
            throw new ConfigException("cannot migrate config.yml from version " + fromVersion, ex);
        }
    }
}
