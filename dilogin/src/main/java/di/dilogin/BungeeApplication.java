package di.dilogin;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import di.dicore.api.DIApi;
import di.dicore.api.impl.DIApiBungeeImpl;
import di.dilogin.controller.ConfigValidator;
import di.dilogin.controller.DBController;
import di.dilogin.controller.MainController;
import di.dilogin.controller.SchemaController;
import di.dilogin.controller.impl.DILoginControllerBungeeImpl;
import di.dilogin.controller.impl.DiscordControllerImpl;
import di.dilogin.discord.command.DiscordRegisterBungeeCommand;
import di.dilogin.discord.command.PrejoinConfirmDiscordCommand;
import di.dilogin.discord.command.UserInfoDiscordCommand;
import di.dilogin.discord.command.UserListDiscordCommand;
import di.dilogin.discord.event.PrejoinButtonListener;
import di.dilogin.discord.event.PrejoinLoginReactionListener;
import di.dilogin.discord.event.UserLoginReactionMessageBungeeEvent;
import di.dilogin.discord.event.WhitelistGateButtonListener;
import di.dilogin.discord.status.BungeeServerSnapshotProvider;
import di.dilogin.discord.status.ServerStatusEmbedTask;
import di.dilogin.discord.util.DiscordDmCoordinator;
import di.dilogin.discord.util.SlashCommandsConfiguration;
import di.dilogin.minecraft.bungee.command.ForceLoginBungeeCommand;
import di.dilogin.minecraft.bungee.command.RegisterBungeeCommand;
import di.dilogin.minecraft.bungee.command.UnregisterBungeeCommand;
import di.dilogin.minecraft.bungee.command.UserInfoBungeeCommand;
import di.dilogin.minecraft.bungee.controller.ChannelMessageController;
import di.dilogin.minecraft.bungee.event.PrejoinVerificationBungeeListener;
import di.dilogin.minecraft.bungee.event.UserLeaveBungeeEvent;
import di.dilogin.minecraft.bungee.event.UserLoginBungeeEvent;
import di.dilogin.minecraft.cache.PrejoinCache;
import di.dilogin.minecraft.cache.TmpCache;
import di.dilogin.minecraft.ext.fastlogin.FastLoginHook;
import di.dilogin.minecraft.ext.luckperms.LuckPermsEvents;
import di.dilogin.minecraft.ext.luckperms.LuckPermsLoginBungeeEvent;
import di.internal.exception.NoApiException;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * Main DILogin class for Bungee.
 */
public class BungeeApplication extends Plugin {

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
		plugin = this;

		connectWithCoreApi();
		
		MainController.setDIApi(api);
		MainController.setDILoginController(new DILoginControllerBungeeImpl());
		MainController.setDiscordController(new DiscordControllerImpl());
		MainController.setBukkit(true);

		if (!ConfigValidator.validateAndLog(api, this.getClass().getClassLoader(), api.getInternalController().getDataFolder(), getLogger())) {
			plugin.onDisable();
			return;
		}

		DBController.getConnect();
		if (SchemaController.check(getDescription().getVersion(), getLogger())
				== SchemaController.Result.REJECTED_NEWER_DB) {
			plugin.onDisable();
			return;
		}
		logFastLoginStatus();
		initDiscordEvents();
		initDiscordCommands();
		initDiscordSlashCommands();
		initCommands();
		initEvents();
		scheduleStatusEmbed();

		// Events to connect servers with proxy.
		plugin.getProxy().getPluginManager().registerListener(plugin, new ChannelMessageController());

		getLogger().info("Plugin started");
	}

	@Override
	public void onDisable() {
		TmpCache.clearAll();
	}

	/**
	 * @return Get Main Bungee plugin.
	 */
	public static Plugin getPlugin() {
		return plugin;
	}

	/**
	 * Connect with DIApi.
	 */
	private void connectWithCoreApi() {
		if (plugin.getProxy().getPluginManager().getPlugin("DICore") != null) {
			try {
				api = new DIApiBungeeImpl(plugin, this.getClass().getClassLoader(), true, true);
			} catch (NoApiException e) {
	            MainController.getDIApi().getInternalController().getLogger().log(Level.SEVERE,"BungeeApplication - connectWithCoreApi",e);
			}
		} else {
			plugin.getLogger().log(Level.SEVERE,
					"Failed to connect to DICore plugin. Check if it has been turned on correctly.");
			plugin.onDisable();
		}
	}

	/**
	 * Init main Bungee commands.
	 */
	private void initCommands() {
		this.getProxy().getPluginManager().registerCommand(this, new RegisterBungeeCommand());
		this.getProxy().getPluginManager().registerCommand(this, new UnregisterBungeeCommand());
		this.getProxy().getPluginManager().registerCommand(this, new ForceLoginBungeeCommand());
		this.getProxy().getPluginManager().registerCommand(this, new UserInfoBungeeCommand());
	}

	/**
	 * Init main Bungee events.
	 */
	private void initEvents() {
		this.getProxy().getPluginManager().registerListener(this, new UserLoginBungeeEvent());
		this.getProxy().getPluginManager().registerListener(this, new UserLeaveBungeeEvent());
		if (MainController.getDILoginController().isLuckPermsEnabled()) {
			initLuckPermsEvents();
		}
		if (isPrejoinVerificationEnabled()) {
			this.getProxy().getPluginManager().registerListener(this, new PrejoinVerificationBungeeListener());
			schedulePrejoinPurge();
			getLogger().info("Prejoin verification enabled: unregistered players will be kicked and must confirm via Discord.");
		}
	}

	private boolean isPrejoinVerificationEnabled() {
		try {
			return api.getInternalController().getConfigManager().contains("prejoin_verification")
					&& api.getInternalController().getConfigManager().getBoolean("prejoin_verification");
		} catch (Exception ignored) {
			return false;
		}
	}

	private void schedulePrejoinPurge() {
		this.getProxy().getScheduler().schedule(this, PrejoinCache::purgeExpired, 5, 5, TimeUnit.MINUTES);
	}
	
	/**
	 * Init events with compatibility with LuckPerms.
	 */
	private void initLuckPermsEvents() {
		if (MainController.getDILoginController().isSyncroRolEnabled()) {
			getPlugin().getLogger().info("LuckPerms detected, starting plugin compatibility.");
			new LuckPermsEvents(getPlugin());
			this.getProxy().getPluginManager().registerListener(this, new LuckPermsLoginBungeeEvent());
		}
	}
	
	/**
	 * Init slash commands.
	 */
	private void initDiscordSlashCommands() {
		if (MainController.getDILoginController().isSlashCommandsEnabled()) {
			SlashCommandsConfiguration.configureSlashCommands(api);
			api.registerDiscordSlashCommand(new DiscordRegisterBungeeCommand());
			api.registerDiscordSlashCommand(new UserInfoDiscordCommand());
			api.registerDiscordSlashCommand(new UserListDiscordCommand());
			if (isPrejoinVerificationEnabled()) {
				api.registerDiscordSlashCommand(new PrejoinConfirmDiscordCommand());
			}
		}
	}

	/**
	 * Records Discord events.
	 */
	private void initDiscordEvents() {
		api.registerDiscordEvent(new UserLoginReactionMessageBungeeEvent());
		if (isPrejoinVerificationEnabled()) {
			api.registerDiscordEvent(new PrejoinLoginReactionListener());
			api.registerDiscordEvent(new PrejoinButtonListener());
		}
		if (isWhitelistGateEnabled()) {
			api.registerDiscordEvent(new WhitelistGateButtonListener());
		}
		if (MainController.getDILoginController().isSyncroRolEnabled()) {

		}
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
				new BungeeServerSnapshotProvider(),
				api.getInternalController().getDataFolder());
		this.getProxy().getScheduler().schedule(this, task,
				intervalSec, intervalSec, TimeUnit.SECONDS);
		this.getProxy().getScheduler().schedule(this, DiscordDmCoordinator::purgeStale,
				5, 5, TimeUnit.MINUTES);
		getLogger().info("Status embed enabled (interval " + intervalSec + "s).");
	}

	/**
	 * Init discord commands.
	 */
	private void initDiscordCommands() {
		api.registerDiscordCommand(new DiscordRegisterBungeeCommand());
		api.registerDiscordCommand(new UserInfoDiscordCommand());
		if (isPrejoinVerificationEnabled()) {
			api.registerDiscordCommand(new PrejoinConfirmDiscordCommand());
		}
	}
}
