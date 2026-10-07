package ray.labs.rayauction.platform;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ray.labs.rayauction.domain.port.OnlinePort;

public final class PaperOnlinePort implements OnlinePort {

    private final Scheduler scheduler;

    public PaperOnlinePort(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public boolean isOnline(UUID playerId) {
        return Bukkit.getPlayer(playerId) != null;
    }

    @Override
    public long currentExperience(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return 0L;
        }
        return scheduler.supplyFor(player, player::calculateTotalExperiencePoints).join();
    }

    @Override
    public void setExperience(UUID playerId, long totalExperience) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        int value = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, totalExperience));
        scheduler.runFor(player, () -> player.setExperienceLevelAndProgress(value));
    }

    @Override
    public Optional<String> name(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return Optional.of(player.getName());
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline.getName();
        return Optional.ofNullable(name);
    }
}
