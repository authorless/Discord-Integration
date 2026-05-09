package di.dilogin;

import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import di.dicore.api.DIApi;
import di.dicore.api.impl.DIApiBukkitImpl;
import di.dilogin.controller.ConfigValidator;
import di.dilogin.controller.DBController;
import di.dilogin.controller.MainController;
import di.dilogin.controller.SchemaController;
import di.dilogin.controller.impl.DILoginControllerBukkitImpl;
import di.dilogin.controller.impl.DiscordControllerImpl;
import di.dilogin.discord.command.DiscordRegisterBukkitCommand;
import di.dilogin.discord.command.PrejoinConfirmDiscordCommand;
import di.dilogin.discord.command.UserInfoDiscordCommand;
import di.dilogin.discord.command.UserListDiscordCommand;
import di.dilogin.discord.event.PrejoinButtonListener;
import di.dilogin.discord.event.PrejoinLoginReactionListener;
import di.dilogin.discord.event.UserLoginReactionMessageBukkitEvent;
import di.dilogin.discord.event.WhitelistGateButtonListener;
import di.dilogin.discord.status.BukkitServerSnapshotProvider;
import di.dilogin.discord.status.ServerStatusEmbedTask;
import di.dilogin.discord.util.DiscordDmCoordinator;
import di.dilogin.discord.util.SlashCommandsConfiguration;
import di.dilogin.minecraft.bukkit.command.ForceLoginBukkitCommand;
import di.dilogin.minecraft.bukkit.command.RegisterBukkitCommand;
import di.dilogin.minecraft.bukkit.command.UnregisterBukkitCommand;
import di.dilogin.minecraft.bukkit.command.UserInfoBukkitCommand;
import di.dilogin.minecraft.bukkit.event.UserBlockEvents;
import di.dilogin.minecraft.bukkit.event.UserLeaveEvent;
import di.dilogin.minecraft.bukkit.event.PrejoinAuthmeJoinListener;
import di.dilogin.minecraft.bukkit.event.PrejoinVerificationListener;
import di.dilogin.minecraft.bukkit.event.UserPreLoginEvent;
import di.dilogin.minecraft.bukkit.event.UserTeleportEvents;
import di.dilogin.minecraft.bukkit.event.impl.UserLoginExternEventImpl;
import di.dilogin.minecraft.bukkit.event.impl.UserLoginInternEventImpl;
import di.dilogin.minecraft.cache.PrejoinCache;
import di.dilogin.minecraft.cache.TmpCache;
import di.dilogin.minecraft.ext.authme.AuthmeEvents;
import di.dilogin.minecraft.ext.authme.UserLoginEventAuthmeImpl;
import di.dilogin.minecraft.ext.fastlogin.FastLoginHook;
import di.dilogin.minecraft.ext.luckperms.GuildMemberRoleEvent;
import di.dilogin.minecraft.ext.luckperms.LuckPermsEvents;
import di.dilogin.minecraft.ext.luckperms.LuckPermsLoginBukkitEvent;
import di.dilogin.minecraft.ext.nlogin.UnregisterNLoginEvents;
import di.dilogin.minecraft.ext.nlogin.UserLoginEventNLoginImpl;
import di.internal.exception.NoApiException;

/**
 * Main DILogin class for Bukkit.
 */
public class BukkitApplication extends JavaPlugin {

	/**
	 * Discord Integration Core Api.
	 */
	private static DIApi api;

	/**
	 * Main DILogin plugin.
	 */
	private static Plugin plugin;

	@Override
	public void onEnable() {
		plugin = getPlugin(getClass());

		connectWithCoreApi();
		if (api == null || !isEnabled()) {
			// DICore was missing or disabled — already logged. Bail before touching anything.
			return;
		}

		MainController.setDIApi(api);
		MainController.setDILoginController(new DILoginControllerBukkitImpl());
		MainController.setBukkit(true);

		if (!ConfigValidator.validateAndLog(api, this.getClassLoader(), api.getInternalController().getDataFolder(), getLogger())) {
			plugin.getPluginLoader().disablePlugin(plugin);
			return;
		}

		if (!api.isBungeeDetected()) {
			// If api is in own server.
			MainController.setDiscordController(new DiscordControllerImpl());
			// Schema check below acquires (and properly releases) the first pooled connection,
			// which is enough to fail fast if the DB is unreachable. Calling DBController.getConnect()
			// directly here would leak that connection and starve the SQLite pool (size = 1).
			if (!checkSchemaCompatibility()) {
				plugin.getPluginLoader().disablePlugin(plugin);
				return;
			}
			logFastLoginStatus();
			initInternCommands();
			initInternEvents();
			initDiscordEvents();
			initDiscordCommands();
			initDiscordSlashCommands();
			scheduleStatusEmbed();
		} else {
			// If api is in proxy.
			initExtEvents();
			api.getInternalController().initConnectionWithBungee();
		}
		getLogger().info("Plugin started");
	}

	@Override
	public void onDisable() {
		TmpCache.clearAll();
	}

	/**
	 * @return Get Main Bukkit plugin.
	 */
	public static Plugin getPlugin() {
		return plugin;
	}

	/**
	 * Add the commands to bukkit.
	 */
	private void initInternCommands() {
		initUniqueCommand("diregister", new RegisterBukkitCommand());
		initUniqueCommand("forcelogin", new ForceLoginBukkitCommand());
		initUniqueCommand("unregister", new UnregisterBukkitCommand());
		initUniqueCommand("userinfo", new UserInfoBukkitCommand());
	}

	/**
	 * @@ -103,10 +114,28 @@ private void initInternCommands() {
	 * @param command  Bukkit command.
	 * @param executor CommandExecutor.
	 */
	private void initUniqueCommand(String command, CommandExecutor executor) {
		Objects.requireNonNull(getCommand(command)).setExecutor(executor);
		Objects.requireNonNull(getCommand(command))
				.setPermissionMessage(api.getCoreController().getLangManager().getString("no_permission"));
	}

	/**
	 * Connect with DIApi.
	 */
	private void connectWithCoreApi() {
		try {
			if (Objects.requireNonNull(plugin.getServer().getPluginManager().getPlugin("DICore")).isEnabled()) {
				api = new DIApiBukkitImpl(plugin, this.getClassLoader(), true, true);
			} else {
				plugin.getLogger().log(Level.SEVERE,
						"Failed to connect to DICore plugin. Check if it has been turned on correctly.");
				plugin.getPluginLoader().disablePlugin(plugin);
			}
		} catch (NoApiException e) {
			getLogger().log(Level.SEVERE, "BukkitApplication - connectWithCoreApi", e);
		}
	}

	/**
	 * Init Bukkit events.
	 */
	private void initInternEvents() {
		if (MainController.getDILoginController().isLuckPermsEnabled()) {
			initLuckPermsEvents();
		}
		if (MainController.getDILoginController().isAuthmeEnabled()) {
			initAuthmeEvents();
		} else if (MainController.getDILoginController().isNLoginEnabled()) {
			initNLoginEvents();
		} else {
			getServer().getPluginManager().registerEvents(new UserLoginInternEventImpl(), plugin);
		}
		getServer().getPluginManager().registerEvents(new UserBlockEvents(), plugin);
		getServer().getPluginManager().registerEvents(new UserLeaveEvent(), plugin);
		getServer().getPluginManager().registerEvents(new UserTeleportEvents(), plugin);
		getServer().getPluginManager().registerEvents(new UserPreLoginEvent(), plugin);
		if (isPrejoinVerificationEnabled()) {
			getServer().getPluginManager().registerEvents(new PrejoinVerificationListener(), plugin);
			if (MainController.getDILoginController().isAuthmeEnabled()) {
				getServer().getPluginManager().registerEvents(new PrejoinAuthmeJoinListener(), plugin);
				getLogger().info("Prejoin verification + AuthMe: registration deferred to first successful join.");
			}
			schedulePrejoinPurge();
			getLogger().info("Prejoin verification enabled: unregistered players are kicked and must confirm via Discord.");
		}
	}

	private void schedulePrejoinPurge() {
		long ticks = 20L * 60L * 5L; // 5 minutes
		getServer().getScheduler().runTaskTimerAsynchronously(plugin, PrejoinCache::purgeExpired, ticks, ticks);
	}

	private void logFastLoginStatus() {
		boolean installed = FastLoginHook.isInstalled();
		boolean bypass = FastLoginHook.isBypassEnabled();
		if (installed && bypass) {
			getLogger().info("FastLogin detected — premium bypass is active. Verified premium players will skip the Discord verification flow.");
		} else if (installed) {
			getLogger().info("FastLogin detected. Set bypass_premium_login: true in config.yml to skip Discord verification for premium players.");
		} else if (bypass) {
			getLogger().warning("bypass_premium_login is true but FastLogin is not installed — the option is ignored to prevent name spoofing.");
		}
	}

	private boolean checkSchemaCompatibility() {
		SchemaController.Result result = SchemaController.check(getDescription().getVersion(), getLogger());
		return result != SchemaController.Result.REJECTED_NEWER_DB;
	}

	private boolean isPrejoinVerificationEnabled() {
		try {
			return api.getInternalController().getConfigManager().contains("prejoin_verification")
					&& api.getInternalController().getConfigManager().getBoolean("prejoin_verification");
		} catch (Exception ignored) {
			return false;
		}
	}

	/**
	 * Init Bukkit events when api is in Proxy.
	 */
	private void initExtEvents() {
		getServer().getPluginManager().registerEvents(new UserBlockEvents(), plugin);
		getServer().getPluginManager().registerEvents(new UserLoginExternEventImpl(plugin), plugin);
	}

	/**
	 * Records Discord events.
	 */
	private void initDiscordEvents() {
		api.registerDiscordEvent(new UserLoginReactionMessageBukkitEvent());
		if (isPrejoinVerificationEnabled()) {
			api.registerDiscordEvent(new PrejoinLoginReactionListener());
			api.registerDiscordEvent(new PrejoinButtonListener());
		}
		if (isWhitelistGateEnabled()) {
			api.registerDiscordEvent(new WhitelistGateButtonListener());
		}
		if (MainController.getDILoginController().isSyncroRolEnabled()) {
			api.registerDiscordEvent(new GuildMemberRoleEvent());
		}
	}

	private boolean isWhitelistGateEnabled() {
		try {
			return api.getInternalController().getConfigManager().contains("whitelist_gate_enabled")
					&& api.getInternalController().getConfigManager().getBoolean("whitelist_gate_enabled");
		} catch (Exception ignored) {
			return false;
		}
	}

	private boolean isStatusEmbedEnabled() {
		try {
			return api.getInternalController().getConfigManager().contains("status_embed_enabled")
					&& api.getInternalController().getConfigManager().getBoolean("status_embed_enabled");
		} catch (Exception ignored) {
			return false;
		}
	}

	private void scheduleStatusEmbed() {
		if (!isStatusEmbedEnabled()) return;
		long intervalSec = 60L;
		try {
			if (api.getInternalController().getConfigManager().contains("status_embed_interval_sec")) {
				intervalSec = Math.max(15L, api.getInternalController().getConfigManager()
						.getInt("status_embed_interval_sec"));
			}
		} catch (Exception ignored) {
			// fallback
		}
		ServerStatusEmbedTask task = new ServerStatusEmbedTask(api,
				new BukkitServerSnapshotProvider(),
				api.getInternalController().getDataFolder());
		long ticks = intervalSec * 20L;
		getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, ticks, ticks);
		getServer().getScheduler().runTaskTimerAsynchronously(plugin,
				DiscordDmCoordinator::purgeStale, 20L * 60L * 5L, 20L * 60L * 5L);
		getLogger().info("Status embed enabled (interval " + intervalSec + "s).");
	}

	/**
	 * Init discord commands.
	 */
	private void initDiscordCommands() {
		api.registerDiscordCommand(new DiscordRegisterBukkitCommand());
		api.registerDiscordCommand(new UserInfoDiscordCommand());
		api.registerDiscordCommand(new UserListDiscordCommand());
		if (isPrejoinVerificationEnabled()) {
			api.registerDiscordCommand(new PrejoinConfirmDiscordCommand());
		}
	}
	
	/**
	 * Init slash commands.
	 */
	private void initDiscordSlashCommands() {
		if (MainController.getDILoginController().isSlashCommandsEnabled()) {
			SlashCommandsConfiguration.configureSlashCommands(api);
			api.registerDiscordSlashCommand(new DiscordRegisterBukkitCommand());
			api.registerDiscordSlashCommand(new UserInfoDiscordCommand());
			api.registerDiscordSlashCommand(new UserListDiscordCommand());
			if (isPrejoinVerificationEnabled()) {
				api.registerDiscordSlashCommand(new PrejoinConfirmDiscordCommand());
			}
		}
	}

	/**
	 * Init events with compatibility with nLogin.
	 */
	private void initNLoginEvents() {
		getPlugin().getLogger().info("nLogin detected, starting plugin compatibility.");
		try {
			Class.forName("com.nickuc.login.api.nLoginAPIHolder");
			getServer().getPluginManager().registerEvents(new UnregisterNLoginEvents(), plugin);
			getServer().getPluginManager().registerEvents(new UserLoginEventNLoginImpl(), plugin);
		} catch (ClassNotFoundException e) {
			getLogger().severe("You are using the old version of nLogin.");
			getLogger().severe("Please upgrade to version 10.");
		}
	}

	/**
	 * Init events with compatibility with LuckPerms.
	 */
	private void initLuckPermsEvents() {
		if (MainController.getDILoginController().isSyncroRolEnabled()) {
			getPlugin().getLogger().info("LuckPerms detected, starting plugin compatibility.");
			new LuckPermsEvents(getPlugin());
			getServer().getPluginManager().registerEvents(new LuckPermsLoginBukkitEvent(), plugin);
		}
	}

	/**
	 * Init events with compatibility with Authme.
	 */
	private void initAuthmeEvents() {
		getPlugin().getLogger().info("Authme detected, starting plugin compatibility.");
		getServer().getPluginManager().registerEvents(new UserLoginEventAuthmeImpl(), plugin);
		getServer().getPluginManager().registerEvents(new AuthmeEvents(), plugin);
	}

}
