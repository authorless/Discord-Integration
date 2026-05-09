package di.dilogin.minecraft.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import di.dilogin.entity.TmpMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Contains users who are in the process of registering / logging in
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TmpCache {

	/**
	 * Sentinel placed in the cache when a callsite wants to mark a player as
	 * "pending" before the real {@link TmpMessage} is built. Required because
	 * {@link ConcurrentHashMap} disallows null values.
	 */
	private static final TmpMessage PLACEHOLDER = new TmpMessage("", null, null, "");

	/**
	 * List of users pending login.
	 */
	private static final Map<String, TmpMessage> loginUserList = new ConcurrentHashMap<>();

	/**
	 * List of users pending registration.
	 */
	private static final Map<String, TmpMessage> registerUserList = new ConcurrentHashMap<>();

	/**
	 * Add a player to the pending registration list.
	 *
	 * @param playerName Bukkit player's name.
	 */
	public static void addRegister(String playerName, TmpMessage message) {
		registerUserList.put(playerName, message != null ? message : PLACEHOLDER);
	}

	/**
	 * Add a player to the pending login list.
	 *
	 * @param playerName Bukkit player's name.
	 */
	public static void addLogin(String playerName, TmpMessage message) {
		loginUserList.put(playerName, message != null ? message : PLACEHOLDER);
	}

	/**
	 * Remove a player to the pending register list.
	 * 
	 * @param playerName Bukkit player's name.
	 */
	public static void removeRegister(String playerName) {
		registerUserList.remove(playerName);
	}

	/**
	 * Remove a player to the pending login list.
	 * 
	 * @param playerName Bukkit player's name.
	 */
	public static void removeLogin(String playerName) {
		loginUserList.remove(playerName);
	}

	/**
	 * Check if there is a player on the pending registration list.
	 * 
	 * @param playerName Bukkit player's name.
	 * @return true if there is a player.
	 */
	public static boolean containsRegister(String playerName) {
		return registerUserList.containsKey(playerName);
	}

	/**
	 * Check if there is a player on the pending login list.
	 * 
	 * @param playerName Bukkit player's name.
	 * @return true if there is a player.
	 */
	public static boolean containsLogin(String playerName) {
		return loginUserList.containsKey(playerName);
	}

	/**
	 * @param playerName Bukkit player's name.
	 * @return Gets the possible message.
	 */
	public static Optional<TmpMessage> getRegisterMessage(String playerName) {
		return unwrap(registerUserList.get(playerName));
	}

	/**
	 * @param playerName Bukkit player's name.
	 * @return Gets the possible message.
	 */
	public static Optional<TmpMessage> getLoginMessage(String playerName) {
		return unwrap(loginUserList.get(playerName));
	}

	/**
	 * @param id Message id sent in the registration request.
	 * @return Possible register message.
	 */
	public static Optional<TmpMessage> getRegisterMessage(long id) {
		return registerUserList.values().stream()
				.filter(tmp -> tmp != PLACEHOLDER && tmp.getMessage() != null
						&& tmp.getMessage().getIdLong() == id)
				.findFirst();
	}

	/**
	 * @param id Message id sent in the login request.
	 * @return Possible login message.
	 */
	public static Optional<TmpMessage> getLoginMessage(long id) {
		return loginUserList.values().stream()
				.filter(tmp -> tmp != PLACEHOLDER && tmp.getMessage() != null
						&& tmp.getMessage().getIdLong() == id)
				.findFirst();
	}

	/**
	 * @param code Code registration.
	 * @return Possible register message.
	 */
	public static Optional<TmpMessage> getRegisterMessageByCode(String code) {
		return registerUserList.values().stream()
				.filter(tmp -> tmp != PLACEHOLDER && code.equals(tmp.getCode()))
				.findFirst();
	}

	/**
	 * Delete all messages.
	 */
	public static void clearAll() {
		loginUserList.values().forEach(tmpMessage -> {
			if (tmpMessage != PLACEHOLDER && tmpMessage.getMessage() != null)
				tmpMessage.getMessage().delete().queue(null, err -> { /* ignore */ });
		});
		loginUserList.clear();
		registerUserList.values().forEach(tmpMessage -> {
			if (tmpMessage != PLACEHOLDER && tmpMessage.getMessage() != null)
				tmpMessage.getMessage().delete().queue(null, err -> { /* ignore */ });
		});
		registerUserList.clear();
	}

	private static Optional<TmpMessage> unwrap(TmpMessage value) {
		if (value == null || value == PLACEHOLDER)
			return Optional.empty();
		return Optional.of(value);
	}

}
