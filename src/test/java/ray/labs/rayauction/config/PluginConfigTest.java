package ray.labs.rayauction.config;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ray.labs.rayauction.domain.BlacklistPolicy;
import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.DurationSpec;
import ray.labs.rayauction.storage.DatabaseType;

import static org.assertj.core.api.Assertions.assertThat;

class PluginConfigTest {

    @TempDir
    Path dataFolder;

    private List<String> install() {
        List<String> missing = new ArrayList<>();
        PluginConfig.installDefaults(dataFolder, missing);
        return missing;
    }

    @Test
    void installsAllBundledFilesOnce() throws IOException {
        assertThat(install()).containsExactlyInAnyOrder("config.yml", "messages.yml", "blacklist.yml", "gui.yml");
        assertThat(Files.exists(dataFolder.resolve("config.yml"))).isTrue();

        assertThat(install()).isEmpty();
    }

    @Test
    void loadsBundledDefaults() {
        install();

        PluginConfig config = PluginConfig.load(dataFolder);

        assertThat(config.configVersion()).isEqualTo(PluginConfig.CURRENT_VERSION);
        assertThat(config.database().type()).isEqualTo(DatabaseType.H2);
        assertThat(config.locale()).isEqualTo("ru");
        assertThat(config.debug()).isFalse();
        assertThat(config.multiServer().enabled()).isFalse();
        assertThat(config.discord().enabled()).isFalse();
        assertThat(config.commands().auctionAliases()).contains("ahouse", "auction");
        assertThat(config.commands().adminAliases()).contains("rayauctionadmin");
    }

    @Test
    void loadsSoundsWithDefaults() {
        install();

        PluginConfig config = PluginConfig.load(dataFolder);

        assertThat(config.sounds().get("buy").enabled()).isTrue();
        assertThat(config.sounds().get("buy").name()).isEqualTo("entity.player.levelup");
        assertThat(config.sounds().get("buy").pitch()).isEqualTo(1.4f);
        assertThat(config.sounds().get("claim").enabled()).isTrue();
        assertThat(config.sounds().get("error").name()).isEqualTo("entity.villager.no");
        assertThat(config.sounds().get("missing-key").enabled()).isFalse();
        assertThat(config.sounds().get(null).enabled()).isFalse();
    }

    @Test
    void loadsAuctionRulesFromDefaults() {
        install();

        PluginConfig config = PluginConfig.load(dataFolder);

        assertThat(config.auction().rules().taxRate()).isEqualByComparingTo(new BigDecimal("0.05"));
        assertThat(config.auction().rules().defaultLimit()).isEqualTo(10);
        assertThat(config.auction().rules().browserPageSize()).isEqualTo(36);
        List<DurationSpec> durations = config.auction().rules().durations();
        assertThat(durations).hasSize(4);
        assertThat(durations.get(0).seconds()).isEqualTo(6 * 3600L);
        assertThat(durations.get(3).seconds()).isEqualTo(7 * 86_400L);
        assertThat(config.auction().rules().priceBounds()).containsKey("experience");
        assertThat(config.auction().rules().fallbackPriceBounds().min())
                .isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void loadsCurrenciesInConfiguredOrder() {
        install();

        PluginConfig config = PluginConfig.load(dataFolder);
        List<CurrencySettings> currencies = config.economy().currencies();

        assertThat(currencies).isNotEmpty();
        assertThat(currencies.get(0).kind()).isEqualTo(Currency.Experience.ID);
        assertThat(config.economy().catalog().byId("experience")).isPresent();
        assertThat(config.economy().catalog().byId("vault")).isPresent();
        assertThat(config.economy().catalog().ids()).containsExactly("experience", "vault");
    }

    @Test
    void loadsGuiLayoutWithoutSlotCollisions() {
        install();

        GuiConfig gui = GuiConfig.from(YamlNode.load(dataFolder.resolve("gui.yml")));

        assertThat(gui.itemSlotStart()).isEqualTo(9);
        assertThat(gui.itemSlotEnd()).isEqualTo(44);
        assertThat(gui.itemSlots()).hasSize(36);
        assertThat(gui.fillEnabled()).isTrue();
        assertThat(gui.menu("browser", "gui.browser.title", 54).size()).isEqualTo(54);
        assertThat(gui.menu("confirm", "gui.confirm.title", 27).size()).isEqualTo(27);
        assertThat(gui.button("category-weapons")).isPresent();
        assertThat(gui.button("currency-0").orElseThrow().slot()).isBetween(45, 53);
        assertThat(gui.button("duration-0").orElseThrow().slot()).isBetween(45, 53);
        assertThat(gui.button("preview-back").orElseThrow().slot()).isEqualTo(49);
        assertThat(gui.button("history-back").orElseThrow().slot()).isEqualTo(45);

        for (GuiConfig.ButtonConfig button : gui.buttons().values()) {
            if ("browser".equals(button.menu())) {
                assertThat(button.slot() < gui.itemSlotStart() || button.slot() > gui.itemSlotEnd())
                        .as("browser button %s must not overlap the item area", button.id())
                        .isTrue();
            }
        }
    }

    @Test
    void loadsMessagesWithPrefixRules() {
        install();

        Messages messages = Messages.load(dataFolder.resolve("messages.yml"));

        assertThat(messages.raw("gui.browser.title", null)).isEqualTo("Аукцион");
        assertThat(messages.raw("buy.success", null)).contains("<item>");
        assertThat(messages.raw("gui.button.back.name", null)).isNotBlank();
        assertThat(messages.raw("gui.button.history-filter.name", null)).contains("<filter>");
        assertThat(messages.raw("missing.key", "fallback")).isEqualTo("fallback");
        assertThat(messages.prefix()).isNotBlank();
        assertThat(messages.render("gui.browser.title").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC))
                .isNotEqualTo(net.kyori.adventure.text.format.TextDecoration.State.TRUE);
    }

    @Test
    void loadsBlacklist() {
        install();

        BlacklistConfig blacklist = BlacklistConfig.load(dataFolder.resolve("blacklist.yml"));

        assertThat(blacklist.rawMaterials()).contains("bedrock", "barrier");
        assertThat(blacklist.policy().materials()).contains("BEDROCK", "BARRIER");
        assertThat(blacklist.policy().loreContains()).contains("soulbound");
        assertThat(blacklist.policy().nbtRules()).extracting(BlacklistPolicy.NbtRule::key)
                .contains("minecraft:custom_data");
        assertThat(blacklist.policy().bypassPermission()).isEqualTo("rayauction.blacklist.bypass");
        assertThat(blacklist.policy().blocksShulkerBoxes()).isFalse();
    }

    @Test
    void rejectsConfigFromNewerBuild() throws IOException {
        install();
        Path file = dataFolder.resolve("config.yml");
        Files.writeString(file, Files.readString(file).replace("config-version: 1", "config-version: 99"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PluginConfig.load(dataFolder))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("newer than this build");
    }

    @Test
    void migratesOldConfigAndKeepsBackup() throws IOException {
        install();
        Path file = dataFolder.resolve("config.yml");
        Files.writeString(file, Files.readString(file).replace("config-version: 1", "config-version: 0"));

        PluginConfig config = PluginConfig.load(dataFolder);

        assertThat(config.configVersion()).isEqualTo(PluginConfig.CURRENT_VERSION);
        assertThat(Files.exists(dataFolder.resolve("config.yml.v0.backup"))).isTrue();
        assertThat(Files.readString(file)).contains("config-version: 1");
    }
}
