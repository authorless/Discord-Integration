package di.dilogin.minecraft.cache;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import di.dilogin.minecraft.bukkit.limbo.LimboController;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Contains the list of temporarily blocked users (still pending Discord
 * verification). Concurrent-safe; doubles as the trigger that places the
 * player in limbo when limbo mode is enabled.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserBlockedCache {

	/**
	 * Concurrent set of blocked usernames. Backed by a ConcurrentHashMap so
	 * lookups from async listeners (chat, packets, scheduled tasks) are safe
	 * without extra synchronisation.
	 */
	private static final Set<String> blockedUsers = ConcurrentHashMap.newKeySet();

	/**
	 * Add new blocked user and, if limbo mode is enabled, send the player to
	 * the configured limbo location.
	 *
	 * @param playerName Bukkit Player's name.
	 */
	public static void add(String playerName) {
		blockedUsers.add(playerName);
		Player player = Bukkit.getPlayerExact(playerName);
		if (player != null) {
			LimboController.enter(player);
		}
	}

	/**
	 * Remove blocked user and exit limbo (restoring location, gamemode and
	 * visibility) if applicable.
	 *
	 * @param playerName Bukkit Player's name.
	 */
	public static void remove(String playerName) {
		blockedUsers.remove(playerName);
		LimboController.exitByName(playerName);
	}

	/**
	 * @param playerName Bukkit Player's name.
	 * @return True if player is blocked.
	 */
	public static boolean contains(String playerName) {
		return blockedUsers.contains(playerName);
	}

}
