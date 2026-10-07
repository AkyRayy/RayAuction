package ray.labs.rayauction.domain;

import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.port.EventPublisher;
import ray.labs.rayauction.storage.AuctionRepository;
import ray.labs.rayauction.storage.LimitRepository;

public final class LimitService {

    private final LimitRepository limits;
    private final AuctionRepository auctions;
    private final AuctionRules rules;
    private final EventPublisher publisher;
    private final PermissionLimits permissions;

    public LimitService(
            LimitRepository limits,
            AuctionRepository auctions,
            AuctionRules rules,
            EventPublisher publisher,
            PermissionLimits permissions) {
        this.limits = limits;
        this.auctions = auctions;
        this.rules = rules;
        this.publisher = publisher;
        this.permissions = permissions;
    }

    public interface PermissionLimits {

        int limitFromPermissions(UUID playerId);
    }

    public int limitOf(UUID playerId) {
        Integer custom = limits.find(playerId).orElse(null);
        int permissionLimit = rules.permissionLimits() ? permissions.limitFromPermissions(playerId) : 0;
        return rules.limitFor(custom, permissionLimit);
    }

    public int activeOf(UUID playerId) {
        return auctions.countActive(playerId);
    }

    public int remaining(UUID playerId) {
        int limit = limitOf(playerId);
        if (limit == PlayerLimit.UNLIMITED) {
            return PlayerLimit.UNLIMITED;
        }
        return Math.max(0, limit - activeOf(playerId));
    }

    public Optional<Outcome> ensureCapacity(UUID playerId) {
        int limit = limitOf(playerId);
        if (limit == PlayerLimit.UNLIMITED) {
            return Optional.empty();
        }
        int active = activeOf(playerId);
        if (active < limit) {
            return Optional.empty();
        }
        return Optional.of(Outcome.builder("sell.limit-reached")
                .with("limit", Integer.toString(limit))
                .with("active", Integer.toString(active))
                .build());
    }

    public Result<Integer> set(UUID playerId, int amount) {
        if (amount < PlayerLimit.UNLIMITED) {
            return Result.Failure.of("admin.limit.invalid");
        }
        limits.set(playerId, amount == PlayerLimit.UNLIMITED ? null : amount);
        publisher.limitChanged(playerId, amount);
        return Result.Success.of(amount, Outcome.builder("admin.limit.set")
                .with("amount", Integer.toString(amount))
                .build());
    }

    public Result<Integer> give(UUID playerId, int amount) {
        if (amount <= 0) {
            return Result.Failure.of("admin.limit.invalid");
        }
        limits.add(playerId, limitOf(playerId), amount);
        int now = limitOf(playerId);
        publisher.limitChanged(playerId, now);
        return Result.Success.of(now, Outcome.builder("admin.limit.given")
                .with("amount", Integer.toString(amount))
                .with("total", Integer.toString(now))
                .build());
    }

    public Result<Integer> take(UUID playerId, int amount) {
        if (amount <= 0) {
            return Result.Failure.of("admin.limit.invalid");
        }
        limits.take(playerId, limitOf(playerId), amount);
        int now = limitOf(playerId);
        publisher.limitChanged(playerId, now);
        return Result.Success.of(now, Outcome.builder("admin.limit.taken")
                .with("amount", Integer.toString(amount))
                .with("total", Integer.toString(now))
                .build());
    }

    public Result<Integer> reset(UUID playerId) {
        limits.reset(playerId);
        int now = limitOf(playerId);
        publisher.limitChanged(playerId, now);
        return Result.Success.of(now, Outcome.builder("admin.limit.reset")
                .with("total", Integer.toString(now))
                .build());
    }
}
