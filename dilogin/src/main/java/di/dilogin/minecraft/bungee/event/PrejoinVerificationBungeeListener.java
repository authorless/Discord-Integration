package di.dilogin.minecraft.bungee.event;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.LangController;
import di.dilogin.dao.DIUserDao;
import di.dilogin.entity.CodeGenerator;
import di.dilogin.minecraft.cache.PrejoinCache;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 * Bungee counterpart of {@code PrejoinVerificationListener}: kicks unregistered
 * players on connect and requires them to confirm a code on Discord before the
 * next join attempt.
 */
public class PrejoinVerificationBungeeListener implements Listener {

    private final DIApi api = MainController.getDIApi();
    private final DIUserDao userDao = MainController.getDILoginController().getDIUserDao();

    @EventHandler(priority = EventPriority.LOW)
    public void onLogin(LoginEvent event) {
        String username = event.getConnection().getName();

        if (userDao.contains(username))
            return;

        if (PrejoinCache.consumeVerified(username))
            return;

        String ip = event.getConnection().getAddress() != null
                ? event.getConnection().getAddress().getAddress().getHostAddress()
                : "unknown";
        if (!PrejoinCache.tryAcquire(ip)) {
            event.setCancelled(true);
            event.setCancelReason(TextComponent.fromLegacyText(
                    LangController.getString(username, "prejoin_rate_limited")));
            return;
        }

        long ttlMillis = ttlMinutes() * 60_000L;
        String code = CodeGenerator.getCode(8, api);
        PrejoinCache.addPendingRegister(code, username, ttlMillis);

        String message = LangController.getString(username, "prejoin_register_kick_message")
                .replace("%code%", code)
                .replace("%ttl_min%", String.valueOf(ttlMinutes()));

        event.setCancelled(true);
        event.setCancelReason(TextComponent.fromLegacyText(message));
    }

    private int ttlMinutes() {
        try {
            if (api.getInternalController().getConfigManager().contains("prejoin_verification_code_ttl_min")) {
                return Math.max(1,
                        api.getInternalController().getConfigManager().getInt("prejoin_verification_code_ttl_min"));
            }
        } catch (Exception ignored) {
            // fallback
        }
        return 10;
    }
}
