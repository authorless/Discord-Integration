package di.dilogin.minecraft.bukkit.limbo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import di.dilogin.BukkitApplication;
import di.dilogin.controller.MainController;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Pure-Bukkit "limbo" mode for players still going through the Discord
 * verification flow. Teleports the player to a configured location, switches
 * them to spectator + invisible, and removes them from other players' chat
 * recipient list (handled by {@code LimboChatFilter}).
 *
 * <p>This is not a packet-tight blackout — sounds and distant entity ticks
 * still leak — but it closes the major Bukkit gaps without requiring
 * ProtocolLib.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LimboController {

    private static final Map<UUID, LimboState> states = new ConcurrentHashMap<>();

    public static boolean isEnabled() {
        try {
            return MainController.getDIApi().getInternalController().getConfigManager()
                    .contains("limbo_enabled")
                    && MainController.getDIApi().getInternalController().getConfigManager()
                    .getBoolean("limbo_enabled");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isInLimbo(Player player) {
        return player != null && states.containsKey(player.getUniqueId());
    }

    public static void enter(Player player) {
        if (player == null || !isEnabled() || states.containsKey(player.getUniqueId()))
            return;

        Plugin plugin = BukkitApplication.getPlugin();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || states.containsKey(player.getUniqueId()))
                return;

            LimboState saved = new LimboState(
                    player.getLocation(),
                    player.getGameMode(),
                    player.getAllowFlight(),
                    player.isFlying(),
                    player.isInvisible(),
                    player.isCollidable());
            states.put(player.getUniqueId(), saved);

            Location target = limboLocationFor(player);
            if (target != null) {
                player.teleport(target);
            }
            player.setGameMode(GameMode.SPECTATOR);
            player.setCollidable(false);
            player.setSilent(true);
            player.setInvulnerable(true);

            // Hide from every other online player so they cannot interact.
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.getUniqueId().equals(player.getUniqueId())) continue;
                try {
                    other.hidePlayer(plugin, player);
                } catch (NoSuchMethodError e) {
                    // legacy API fallback (deprecated, keeps 1.13 compat)
                    @SuppressWarnings("deprecation")
                    boolean ignored = legacyHide(other, player);
                }
            }
        });
    }

    public static void exit(Player player) {
        if (player == null) return;
        LimboState saved = states.remove(player.getUniqueId());
        if (saved == null) return;

        Plugin plugin = BukkitApplication.getPlugin();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            if (saved.location != null && saved.location.getWorld() != null) {
                player.teleport(saved.location);
            }
            player.setGameMode(saved.gameMode);
            player.setAllowFlight(saved.allowFlight);
            player.setFlying(saved.flying && saved.allowFlight);
            player.setInvisible(saved.invisible);
            player.setCollidable(saved.collidable);
            player.setSilent(false);
            player.setInvulnerable(false);

            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.getUniqueId().equals(player.getUniqueId())) continue;
                try {
                    other.showPlayer(plugin, player);
                } catch (NoSuchMethodError e) {
                    @SuppressWarnings("deprecation")
                    boolean ignored = legacyShow(other, player);
                }
            }
        });
    }

    public static void exitByName(String playerName) {
        Player p = Bukkit.getPlayerExact(playerName);
        if (p != null) exit(p);
    }

    private static Location limboLocationFor(Player player) {
        try {
            String worldName = MainController.getDIApi().getInternalController().getConfigManager()
                    .getString("limbo_world");
            World world = (worldName == null || worldName.isEmpty())
                    ? player.getWorld()
                    : Bukkit.getWorld(worldName);
            if (world == null) world = player.getWorld();

            double x = readDouble("limbo_x", player.getLocation().getX());
            double y = readDouble("limbo_y", 320.0);
            double z = readDouble("limbo_z", player.getLocation().getZ());
            return new Location(world, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
        } catch (Exception ignored) {
            return new Location(player.getWorld(),
                    player.getLocation().getX(), 320.0, player.getLocation().getZ(),
                    player.getLocation().getYaw(), player.getLocation().getPitch());
        }
    }

    private static double readDouble(String key, double fallback) {
        try {
            if (MainController.getDIApi().getInternalController().getConfigManager().contains(key)) {
                return MainController.getDIApi().getInternalController().getConfigManager().getInt(key);
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return fallback;
    }

    @SuppressWarnings("deprecation")
    private static boolean legacyHide(Player viewer, Player target) {
        viewer.hidePlayer(target);
        return true;
    }

    @SuppressWarnings("deprecation")
    private static boolean legacyShow(Player viewer, Player target) {
        viewer.showPlayer(target);
        return true;
    }

    private static final class LimboState {
        final Location location;
        final GameMode gameMode;
        final boolean allowFlight;
        final boolean flying;
        final boolean invisible;
        final boolean collidable;

        LimboState(Location location, GameMode gameMode, boolean allowFlight, boolean flying,
                   boolean invisible, boolean collidable) {
            this.location = location;
            this.gameMode = gameMode;
            this.allowFlight = allowFlight;
            this.flying = flying;
            this.invisible = invisible;
            this.collidable = collidable;
        }
    }
}
