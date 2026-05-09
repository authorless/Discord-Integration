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
    private static final int BSTATS_PLUGIN_ID = 0;

    @Override
    public void onEnable() {
        Plugin plugin = this.getProxy().getPluginManager().getPlugin("DICore");
        internalController = new CoreControllerBungeeImpl(plugin, this.getClass().getClassLoader());

        BotStatusBungeeEvent.init(plugin);
        initMetrics();

        getLogger().info("Plugin started");
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
