package di.dicore;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import di.dicore.event.BotStatusBukkitEvent;
import di.internal.controller.impl.CoreControllerBukkitImpl;
import net.dv8tion.jda.api.JDA;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import di.internal.controller.CoreController;

/**
 * Main class of the plugin.
 */
public class BukkitApplication extends JavaPlugin {

    /**
     * The internal controller of the core plugin.
     */
    private static CoreController internalController;

    /**
     * The plugin instance.
     */
    private static Plugin plugin;

    /**
     * Runs when the plugin is being powered on.
     */
    /**
     * bStats plugin id. Replace with the real id from https://bstats.org/getting-started
     * once the plugin is registered.
     */
    private static final int BSTATS_PLUGIN_ID = 31219;

    @Override
    public void onEnable() {
        plugin = getPlugin(getClass());
        try {
            internalController = new CoreControllerBukkitImpl(plugin, this.getClassLoader(), false);
        } catch (Throwable t) {
            failFast("DICore could not initialise the controller: " + t.getMessage());
            return;
        }

        // CoreControllerBukkitImpl may invoke disablePlugin() mid-construction when the config
        // is incomplete. Bail before touching anything else, otherwise the closed plugin
        // classloader trips loadClass() with "zip file closed".
        if (!isEnabled()) {
            failFast("DICore failed to start because the configuration is incomplete. Fix config.yml and restart.");
            return;
        }

        if (!isBungeeDetected()) {
            BotStatusBukkitEvent.init(plugin);
        }
        initMetrics();
        getLogger().info("Plugin started");
    }

    /**
     * Marks the plugin as disabled and, if the operator opted in via
     * {@code shutdown_server_on_critical_failure}, halts the entire server so a
     * misconfigured Discord auth never silently flies under the radar.
     */
    private void failFast(String message) {
        getLogger().severe(message);
        try {
            getPluginLoader().disablePlugin(this);
        } catch (Throwable ignored) {
            // already in a bad state
        }
        if (shouldShutdownServerOnFailure()) {
            getLogger().severe("Shutting the server down (shutdown_server_on_critical_failure=true).");
            Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
        }
    }

    private boolean shouldShutdownServerOnFailure() {
        try {
            return internalController != null
                    && internalController.getConfigManager() != null
                    && internalController.getConfigManager().contains("shutdown_server_on_critical_failure")
                    && internalController.getConfigManager().getBoolean("shutdown_server_on_critical_failure");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void initMetrics() {
        if (BSTATS_PLUGIN_ID <= 0)
            return;
        try {
            new Metrics(this, BSTATS_PLUGIN_ID);
        } catch (Exception e) {
            getLogger().warning("Failed to start bStats metrics: " + e.getMessage());
        }
    }

    /**
     * @return The bukkit plugin.
     */
    public static Plugin getPlugin() {
        return plugin;
    }

    /**
     * It is executed when the plugin is being shut down.
     */
    @Override
    public void onDisable() {
        if (internalController != null && internalController.getBot() != null) {
            Optional<JDA> apiOpt = internalController.getBot().getApi();
            if (apiOpt != null && apiOpt.isPresent()) {
                JDA jda = apiOpt.get();
                jda.shutdown();
                try {
                    if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                        jda.shutdownNow();
                        jda.awaitShutdown(5, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    jda.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        getLogger().info("Plugin disabled");
    }

    /**
     * @return the internal controller of the core plugin.
     */
    public static CoreController getInternalController() {
        return internalController;
    }

    /**
     * @return true if BungeeCord setting is enabled.
     */
    private boolean isBungeeDetected() {
        return internalController.getConfigManager().getBoolean("bungeecord");
    }
}
