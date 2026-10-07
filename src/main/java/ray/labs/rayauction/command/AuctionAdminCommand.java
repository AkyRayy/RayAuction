package ray.labs.rayauction.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ray.labs.rayauction.config.Messages;
import ray.labs.rayauction.domain.AuctionService;
import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.CurrencyService;
import ray.labs.rayauction.domain.HistoryFilter;
import ray.labs.rayauction.domain.LimitService;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.domain.Outcome;
import ray.labs.rayauction.domain.Result;
import ray.labs.rayauction.gui.GuiService;
import ray.labs.rayauction.platform.Scheduler;
import ray.labs.rayauction.storage.StatsRepository;
import ray.labs.rayauction.storage.cache.AuctionCache;
import ray.labs.rayauction.text.TextFormatter;

public final class AuctionAdminCommand {

    public static final String PERMISSION = "rayauction.admin";
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private final Supplier<ReloadHandler> reload;
    private final Supplier<LimitService> limits;
    private final Supplier<AuctionService> auctions;
    private final Supplier<CurrencyService> currencies;
    private final Supplier<GuiService> gui;
    private final Supplier<Messages> messages;
    private final Supplier<AuctionCache> cache;
    private final Supplier<StatsRepository> stats;
    private final Supplier<TextFormatter> formatter;
    private final Scheduler scheduler;

    public interface ReloadHandler {

        boolean reload(CommandSender sender);
    }

    public AuctionAdminCommand(
            Supplier<ReloadHandler> reload,
            Supplier<LimitService> limits,
            Supplier<AuctionService> auctions,
            Supplier<CurrencyService> currencies,
            Supplier<GuiService> gui,
            Supplier<Messages> messages,
            Supplier<AuctionCache> cache,
            Supplier<StatsRepository> stats,
            Supplier<TextFormatter> formatter,
            Scheduler scheduler) {
        this.reload = reload;
        this.limits = limits;
        this.auctions = auctions;
        this.currencies = currencies;
        this.gui = gui;
        this.messages = messages;
        this.cache = cache;
        this.stats = stats;
        this.formatter = formatter;
        this.scheduler = scheduler;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ahadmin")
                .requires(source -> source.getSender().hasPermission(PERMISSION));
        root.then(Commands.literal("reload").executes(this::reload));
        root.then(Commands.literal("stats").executes(this::stats));
        root.then(Commands.literal("history")
                .then(Commands.argument("player", ArgumentTypes.playerProfiles())
                        .executes(context -> history(context, 0))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 10_000))
                                .executes(context -> history(context, IntegerArgumentType.getInteger(context, "page") - 1)))));
        LiteralArgumentBuilder<CommandSourceStack> limit = Commands.literal("limit");
        limit.then(playerAction("set", Commands.argument("amount", IntegerArgumentType.integer(-1, 1_000_000))
                .executes(context -> limitAction(context, "set"))));
        limit.then(playerAction("give", Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                .executes(context -> limitAction(context, "give"))));
        limit.then(playerAction("take", Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                .executes(context -> limitAction(context, "take"))));
        limit.then(Commands.argument("player", ArgumentTypes.playerProfiles())
                .then(Commands.literal("reset").executes(context -> limitAction(context, "reset"))));
        root.then(limit);
        root.then(Commands.literal("remove")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(this::remove)));
        root.then(Commands.literal("give")
                .then(Commands.argument("player", ArgumentTypes.playerProfiles())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(this::give))));
        root.then(Commands.literal("currency")
                .then(Commands.argument("id", StringArgumentType.word()).executes(this::currencyInfo)));
        return root.build();
    }

    private RequiredArgumentBuilder<CommandSourceStack, ?> playerAction(
            String action, RequiredArgumentBuilder<CommandSourceStack, ?> amount) {
        return Commands.argument("player", ArgumentTypes.playerProfiles())
                .then(Commands.literal(action).then(amount));
    }

private int stats(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        scheduler.runAsync(() -> {
            AuctionCache auctionCache = cache.get();
            int active = auctionCache.size();
            ray.labs.rayauction.domain.AuctionStats.Leader seller = stats.get().topSeller();
            ray.labs.rayauction.domain.AuctionStats.Leader buyer = stats.get().topBuyer();
            String cheapestItem = auctionCache
                    .cheapest()
                    .map(auction -> auction.item().displayName().isEmpty()
                            ? auction.item().material()
                            : auction.item().displayName())
                    .orElse("\u2014");
            String cheapestPrice = auctionCache
                    .cheapestPrice()
                    .map(money -> formatter.get().money(money.amount(), money.currency().scale()) + " " + money.currency().symbol())
                    .orElse("\u2014");
            Map<String, String> placeholders = Map.of(
                    "active", Integer.toString(active),
                    "seller", seller.isPresent() ? seller.name() : "\u2014",
                    "seller-amount", Long.toString(seller.amount()),
                    "buyer", buyer.isPresent() ? buyer.name() : "\u2014",
                    "buyer-amount", Long.toString(buyer.amount()),
                    "item", cheapestItem,
                    "price", cheapestPrice);
            for (String key : List.of("header", "active", "top-seller", "top-buyer", "cheapest")) {
                sender.sendMessage(messages.get().render("admin.stats." + key, placeholders));
            }
        });
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        boolean success = reload.get().reload(sender);
        sender.sendMessage(messages.get().render(success ? "admin.reload.success" : "admin.reload.failure"));
        return success ? 1 : 0;
    }

    private int history(CommandContext<CommandSourceStack> context, int page) {
        CommandSender sender = context.getSource().getSender();
        Optional<UUID> target = targetId(context);
        if (target.isEmpty()) {
            sender.sendMessage(messages.get().render("command.player-not-found"));
            return 0;
        }
        if (sender instanceof Player player) {
            gui.get().openHistory(player, HistoryFilter.ALL, page);
            return 1;
        }
        auctions.get().history(target.get(), HistoryFilter.ALL, 10).forEach(entry -> sender.sendMessage(
                messages.get().render("admin.history.line", java.util.Map.of(
                        "item", GuiService.displayName(entry.item()),
                        "price", entry.price().amount().toPlainString(),
                        "currency", entry.price().currency().displayName(),
                        "role", entry.role().name(),
                        "status", entry.status().name()))));
        return 1;
    }

    private int limitAction(CommandContext<CommandSourceStack> context, String action) {
        CommandSender sender = context.getSource().getSender();
        Optional<UUID> target = targetId(context);
        if (target.isEmpty()) {
            sender.sendMessage(messages.get().render("command.player-not-found"));
            return 0;
        }
        int amount = 0;
        if (!action.equals("reset")) {
            try {
                amount = IntegerArgumentType.getInteger(context, "amount");
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(messages.get().render("admin.limit.invalid"));
                return 0;
            }
        }
        int requested = amount;
        scheduler.runAsync(() -> {
            Result<Integer> result = switch (action) {
                case "set" -> limits.get().set(target.get(), requested);
                case "give" -> limits.get().give(target.get(), requested);
                case "take" -> limits.get().take(target.get(), requested);
                default -> limits.get().reset(target.get());
            };
            Outcome outcome = result.outcome();
            String name = Bukkit.getOfflinePlayer(target.get()).getName();
            java.util.Map<String, String> placeholders = new java.util.LinkedHashMap<>(outcome.placeholders());
            placeholders.put("player", name == null ? target.get().toString() : name);
            send(sender, outcome.messageKey(), placeholders);
        });
        return 1;
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        long id = IntegerArgumentType.getInteger(context, "id");
        scheduler.runAsync(() -> {
            Result<ray.labs.rayauction.domain.Auction> result =
                    auctions.get().cancel(NIL_UUID, id, true);
            Outcome outcome = result.outcome();
            send(sender, outcome == null ? "admin.remove.done" : outcome.messageKey(), outcome == null
                    ? java.util.Map.of("id", Long.toString(id))
                    : outcome.placeholders());
        });
        return 1;
    }

    private int give(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Optional<UUID> target = targetId(context);
        if (target.isEmpty()) {
            sender.sendMessage(messages.get().render("command.player-not-found"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(context, "amount");
        Optional<Currency> currency = currencies.get().catalog().primary();
        if (currency.isEmpty()) {
            sender.sendMessage(messages.get().render("admin.give.no-currency"));
            return 0;
        }
        Money money = Money.of(BigDecimal.valueOf(amount), currency.get());
        scheduler.runAsync(() -> {
            currencies.get().requireBalancePort(currency.get()).deposit(target.get(), money);
            send(sender, "admin.give.done", java.util.Map.of(
                    "amount", money.amount().toPlainString(),
                    "currency", currency.get().displayName()));
        });
        return 1;
    }

    private int currencyInfo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String id = StringArgumentType.getString(context, "id");
        List<String> known = currencies.get().catalog().ids();
        Optional<Currency> found = currencies.get().resolve(id);
        if (found.isEmpty()) {
            sender.sendMessage(messages.get().render(
                    "admin.currency.unknown", java.util.Map.of("known", String.join(", ", known))));
            return 0;
        }
        Currency currency = found.get();
        sender.sendMessage(messages.get().render("admin.currency.info", java.util.Map.of(
                "id", currency.id(),
                "name", currency.displayName(),
                "symbol", currency.symbol(),
                "available", Boolean.toString(currencies.get().isAvailable(currency)))));
        return 1;
    }

    private void send(CommandSender sender, String key, java.util.Map<String, String> placeholders) {
        if (sender instanceof Player player) {
            scheduler.runFor(player, () -> player.sendMessage(messages.get().render(key, placeholders)));
            return;
        }
        sender.sendMessage(messages.get().render(key, placeholders));
    }

    private Optional<UUID> targetId(CommandContext<CommandSourceStack> context) {
        try {
            io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver resolver =
                    context.getArgument("player",
                            io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver.class);
            var profiles = resolver.resolve(context.getSource());
            if (profiles.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(profiles.iterator().next().getId());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException ex) {
            return Optional.empty();
        }
    }
}
