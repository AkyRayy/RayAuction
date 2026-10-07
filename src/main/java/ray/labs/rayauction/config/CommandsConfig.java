package ray.labs.rayauction.config;

import java.util.List;

public record CommandsConfig(List<String> auctionAliases, List<String> adminAliases) {

    public CommandsConfig {
        auctionAliases = auctionAliases == null ? List.of() : List.copyOf(auctionAliases);
        adminAliases = adminAliases == null ? List.of() : List.copyOf(adminAliases);
    }

    public static CommandsConfig from(YamlNode node) {
        return new CommandsConfig(
                node.stringList("aliases", List.of("ahouse", "auction")),
                node.stringList("admin-aliases", List.of("rayauctionadmin")));
    }
}
