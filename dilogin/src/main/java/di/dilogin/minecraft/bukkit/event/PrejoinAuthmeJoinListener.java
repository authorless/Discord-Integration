package di.dilogin.minecraft.bukkit.event;

import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.minecraft.cache.PrejoinCache;
import di.dilogin.minecraft.ext.authme.AuthmeHook;

/**
 * Finalises an AuthMe registration deferred from the prejoin verification flow.
 *
 * The +confirm command stashes a generated password in the cache and DMs it to
 * the user. This listener fires when the player actually joins and registers
 * them in AuthMe with that password.
 */
public class PrejoinAuthmeJoinListener implements Listener {

    private final DIApi api = MainController.getDIApi();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Optional<String> passwordOpt = PrejoinCache.consumePendingAuthme(player.getName());
        if (!passwordOpt.isPresent())
            return;

        try {
            AuthmeHook.register(player, passwordOpt.get());
            api.getInternalController().getLogger().info(
                    "Completed deferred AuthMe registration for " + player.getName() + " from prejoin flow.");
        } catch (Exception e) {
            api.getInternalController().getLogger().severe(
                    "Failed deferred AuthMe registration for " + player.getName() + ": " + e.getMessage());
        }
    }
}
