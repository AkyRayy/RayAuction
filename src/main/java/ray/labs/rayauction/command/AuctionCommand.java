package ray.labs.rayauction.command;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ray.labs.rayauction.config.Messages;
import ray.labs.rayauction.domain.HistoryFilter;
import ray.labs.rayauction.gui.GuiService;

public final class AuctionCommand {

    public static final String PERMISSION = "rayauction.use";

    private final Supplier<GuiService> gui;
    private final Supplier<Messages> messages;

    public AuctionCommand(Supplier<GuiService> gui, Supplier<Messages> messages) {
        this.gui = gui;
        this.messages = messages;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ah")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(this::openBrowser);
        root.then(Commands.literal("sell")
                .requires(source -> source.getSender().hasPermission("rayauction.sell"))
                .executes(this::openSell));
        root.then(Commands.literal("search")
                .requires(source -> source.getSender().hasPermission("rayauction.search"))
                .executes(this::promptSearch)
                .then(Commands.argument("query", StringArgumentType.greedyString())
                        .executes(this::search)));
        root.then(Commands.literal("view")
                .requires(source -> source.getSender().hasPermission("rayauction.view"))
                .then(Commands.argument("player", ArgumentTypes.playerProfiles())
                        .executes(this::view)));
        root.then(Commands.literal("history")
                .requires(source -> source.getSender().hasPermission("rayauction.history"))
                .executes(this::history));
        root.then(Commands.literal("mailbox").executes(this::mailbox));
        return root.build();
    }

    private int openBrowser(CommandContext<CommandSourceStack> context) {
        return player(context).map(value -> {
            gui.get().openBrowser(value);
            return 1;
        }).orElseGet(() -> consoleOnly(context));
    }

    private int openSell(CommandContext<CommandSourceStack> context) {
        return player(context).map(value -> {
            gui.get().openSell(value);
            return 1;
        }).orElseGet(() -> consoleOnly(context));
    }

    private int promptSearch(CommandContext<CommandSourceStack> context) {
        return player(context).map(value -> {
            gui.get().openSearchPrompt(value);
            return 1;
        }).orElseGet(() -> consoleOnly(context));
    }

    private int search(CommandContext<CommandSourceStack> context) {
        String query = StringArgumentType.getString(context, "query");
        return player(context).map(value -> {
            gui.get().submitSearch(value, query);
            return 1;
        }).orElseGet(() -> consoleOnly(context));
    }

    private int history(CommandContext<CommandSourceStack> context) {
        return player(context).map(value -> {
            gui.get().openHistory(value, HistoryFilter.ALL, 0);
            return 1;
        }).orElseGet(() -> consoleOnly(context));
    }

    private int mailbox(CommandContext<CommandSourceStack> context) {
        return player(context).map(value -> {
            gui.get().openMailbox(value, 0);
            return 1;
        }).orElseGet(() -> consoleOnly(context));
    }

    private int view(CommandContext<CommandSourceStack> context) {
        Optional<Player> sender = player(context);
        if (sender.isEmpty()) {
            return consoleOnly(context);
        }
        PlayerProfileListResolver resolver = context.getArgument("player", PlayerProfileListResolver.class);
        Collection<PlayerProfile> profiles;
        try {
            profiles = resolver.resolve(context.getSource());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException ex) {
            sender.get().sendMessage(messages.get().render("command.player-not-found"));
            return 0;
        }
        if (profiles.isEmpty()) {
            sender.get().sendMessage(messages.get().render("command.player-not-found"));
            return 0;
        }
        PlayerProfile profile = profiles.iterator().next();
        UUID targetId = profile.getId();
        String targetName = profile.getName() == null ? "?" : profile.getName();
        if (targetId == null) {
            sender.get().sendMessage(messages.get().render("command.player-not-found"));
            return 0;
        }
        gui.get().openPlayerListings(sender.get(), targetId, targetName);
        return 1;
    }

    private int consoleOnly(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(messages.get().render("command.players-only"));
        return 0;
    }

    private Optional<Player> player(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        return sender instanceof Player player ? Optional.of(player) : Optional.empty();
    }
}
