package di.dicore;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import di.dicore.event.BotStatusBungeeEvent;
import di.internal.controller.CoreController;
import di.internal.controller.impl.CoreControllerBungeeImpl;
import net.dv8tion.jda.api.JDA;
import net.md_5.bungee.api.plugin.Plugin;
import org.bstats.bungeecord.Metrics;

public class BungeeApplication extends Plugin {

    /**
     * The internal controller of the core plugin.
     */
    private static CoreController internalController;

    /**
     * bStats plugin id. Replace with the real id from https://bstats.org/getting-started
     * once the plugin is registered.
     */
    private static final int BSTATS_PLUGIN_ID = 31220;

    @Override
    public void onEnable() {
        Plugin plugin = this.getProxy().getPluginManager().getPlugin("DICore");
        try {
            internalController = new CoreControllerBungeeImpl(plugin, this.getClass().getClassLoader());
        } catch (Throwable t) {
            failFast("DICore could not initialise the controller: " + t.getMessage());
            return;
        }

        if (internalController == null || internalController.getBot() == null
                || !internalController.getBot().getApi().isPresent()) {
            failFast("DICore failed to start because the configuration is incomplete. Fix config.yml and restart the proxy.");
            return;
        }

        BotStatusBungeeEvent.init(plugin);
        initMetrics();

        getLogger().info("Plugin started");
    }

    private void failFast(String message) {
        getLogger().severe(message);
        try {
            this.onDisable();
        } catch (Throwable ignored) {
            // already in a bad state
        }
        if (shouldShutdownServerOnFailure()) {
            getLogger().severe("Shutting the proxy down (shutdown_server_on_critical_failure=true).");
            this.getProxy().stop("DILogin/DICore failed to start. Fix the configuration.");
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

}
