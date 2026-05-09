package di.dilogin.minecraft.bukkit;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import di.dilogin.BukkitApplication;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Bukkit Util class.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BukkitUtil {

    /** Matches the Minecraft minor version inside any "1.X" or "1.X.Y" substring. */
    private static final Pattern MC_VERSION = Pattern.compile("\\b1\\.(\\d{1,2})(?:\\.\\d+)?\\b");

    /**
     * Extracts the Minecraft minor version from the raw server version string
     * (e.g. {@code "git-Paper-196 (MC: 1.20.1)"} → {@code 20}). Falls back to a
     * high number on failure so callers default to modern-client behaviour
     * (click-to-copy, hover events, ...).
     */
    public static int getServerVersion(String version) {
        if (version == null) return Integer.MAX_VALUE;
        Matcher m = MC_VERSION.matcher(version);
        int latest = -1;
        while (m.find()) {
            try {
                int minor = Integer.parseInt(m.group(1));
                if (minor > latest) latest = minor;
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return latest < 0 ? Integer.MAX_VALUE : latest;
    }

    /**
     * @param playerName Bukkit player's name.
     * @return Possible player based on their name.
     */
    public static Optional<Player> getUserPlayerByName(String playerName) {
        return Optional.ofNullable(BukkitApplication.getPlugin().getServer().getPlayer(playerName));
    }
}
