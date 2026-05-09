package di.dilogin.minecraft.bukkit.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.LangController;
import di.dilogin.dao.DIUserDao;
import di.dilogin.entity.CodeGenerator;
import di.dilogin.minecraft.cache.PrejoinCache;

/**
 * Prejoin verification flow: kicks the player on connect and requires them to
 * complete registration via Discord using a one-time code before letting them
 * in on the next attempt.
 *
 * Registered players bypass this listener and continue through the normal login
 * flow.
 */
public class PrejoinVerificationListener implements Listener {

    private final DIApi api = MainController.getDIApi();
    private final DIUserDao userDao = MainController.getDILoginController().getDIUserDao();

    @EventHandler(priority = EventPriority.LOW)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName();

        if (userDao.contains(username)) {
            // Registered users go through the normal login flow.
            return;
        }

        if (PrejoinCache.consumeVerified(username)) {
            // Player completed verification on Discord; allow this join.
            return;
        }

        long ttlMillis = ttlMinutes() * 60_000L;
        String code = CodeGenerator.getCode(8, api);
        PrejoinCache.addPendingRegister(code, username, ttlMillis);

        String message = LangController.getString(username, "prejoin_register_kick_message")
                .replace("%code%", code)
                .replace("%ttl_min%", String.valueOf(ttlMinutes()));

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, message);
    }

    private int ttlMinutes() {
        try {
            if (api.getInternalController().getConfigManager().contains("prejoin_verification_code_ttl_min")) {
                return Math.max(1, api.getInternalController().getConfigManager().getInt("prejoin_verification_code_ttl_min"));
            }
        } catch (Exception ignored) {
            // fallback
        }
        return 10;
    }
}
