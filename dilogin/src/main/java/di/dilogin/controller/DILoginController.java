package di.dilogin.controller;

import java.util.Optional;

import di.dicore.api.DIApi;
import di.dilogin.dao.DIUserDao;
import di.dilogin.entity.DIUser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

/**
 * DILogin plugin controller.
 */
public interface DILoginController {

	/**
	 *
	 * @return the user dao, class that gets data from the users.
	 */
	DIUserDao getDIUserDao();

	/**
	 * @return The basis for embed messages.
	 */
	EmbedBuilder getEmbedBase();
	
	/**
	 * Check if the session system is enabled.
	 *
	 * @return True if the system is active.
	 */
	default boolean isSessionEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("sessions");
	}
	
	/**
	 * Check if the session file system is enabled.
	 *
	 * @return True if the system is active.
	 */
	default boolean isSessionFileEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("session_persist");
	}

	/**
	 * Check if the rol syncro system is enabled.
	 *
	 * @return True if the system is active.
	 */
	default boolean isSyncroRolEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("syncro_role_enabled");
	}

	/**
	 * Check if syncro name option is enabled in cofig file.
	 *
	 * @return true if its enabled.
	 */
	default boolean isSyncronizeOptionEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("syncro_enable");
	}
	
	/**
	 * Check if register in the server is optional.
	 *
	 * @return true if its enabled.
	 */
	default boolean isRegisterOptionalEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("register_optional_enabled");
	}
	
	/**
	 * Check if register in the server is optional.
	 *
	 * @return true if its enabled.
	 */
	default boolean isRegisterRolListEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("register_role_list_enabled");
	}
	
	/**
	 * Check if user will get some role after 
	 *
	 * @return true if its enabled.
	 */
	default boolean isRegisterGiveRoleEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("register_give_role_enabled");
	}
	
	/**
	 * Check if user will get some role after 
	 *
	 * @return true if its enabled.
	 */
	default boolean isRegisterByNickNameEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("register_by_nickname_enabled");
	}
	
	/**
	 * Check if user will get some role after 
	 *
	 * @return true if its enabled.
	 */
	default boolean isRegisterByDiscordIdEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("register_by_discordid_enabled");
	}

	/**
	 * Check if user will get some role after 
	 *
	 * @return true if its enabled.
	 */
	default boolean isRegisterByDiscordCommandEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("register_by_discord_command_enabled");
	}

	/**
	 * Check if login system is enabled.
	 *
	 * @return true if its enabled.
	 */
	default boolean isLoginSystemEnabled() {
		return MainController.getDIApi().getInternalController().getConfigManager().getBoolean("login_system_enabled");
	}

	/**
	 * @return true is Authme is enabled.
	 */
	boolean isAuthmeEnabled();

	/**
	 * @return true is nLogin is enabled.
	 */
	boolean isNLoginEnabled();

	/**
	 * @return true is LuckPerms is enabled.
	 */
	boolean isLuckPermsEnabled();
	
	/**
	 * @return true is SlashCommands is enabled.
	 */
	boolean isSlashCommandsEnabled();

	/**
	 * Start the player session.
	 * 
	 * @param playerName Bukkit player.
	 * @param user       Discord user.
	 */
	void loginUser(String playerName, User user);

	/**
	 * Kick user from server.
	 *
	 * @param playerName Bukkit player.
	 */
	void kickPlayer(String playerName, String message);

	/**
	 * Syncro minecraft name with discord name.
	 * 
	 * @param playerName Minecraft player name.
	 * @param user       Discord user.
	 */
	default void syncUserName(String playerName, User user) {
		DIApi api = MainController.getDIApi();
		DIUserDao userDao = MainController.getDILoginController().getDIUserDao();
		Optional<DIUser> optDIUser = userDao.get(playerName);

		if (!optDIUser.isPresent())
			return;

		Optional<JDA> jdaOpt = api.getCoreController().getDiscordApi();
		Optional<Guild> guildOpt = api.getCoreController().getGuild();
		if (!jdaOpt.isPresent() || !guildOpt.isPresent())
			return;
		JDA jda = jdaOpt.get();
		Guild guild = guildOpt.get();

		guild.retrieveMemberById(user.getIdLong()).queue(member ->
			guild.retrieveMemberById(jda.getSelfUser().getIdLong()).queue(bot -> {
				if (bot.canInteract(member)) {
					member.modifyNickname(playerName).queue(null, err ->
						api.getInternalController().getLogger()
							.warning("Failed to modify nickname for " + playerName + ": " + err.getMessage()));
				} else {
					api.getInternalController().getLogger()
							.info("Cannot change the nickname of " + playerName + ". Insufficient permissions.");
				}
			}, err -> api.getInternalController().getLogger()
					.warning("Failed to retrieve bot member: " + err.getMessage())),
			err -> api.getInternalController().getLogger()
					.warning("Failed to retrieve member " + user.getIdLong() + ": " + err.getMessage()));
	}
}
