package di.dilogin.minecraft.bungee.event;

import java.util.Optional;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.LangController;
import di.dilogin.dao.DIUserDao;
import di.dilogin.entity.CodeGenerator;
import di.dilogin.entity.DIUser;
import di.dilogin.minecraft.cache.PrejoinCache;
import di.dilogin.minecraft.cache.UserSessionCache;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 * Bungee counterpart of {@code PrejoinVerificationListener}: kicks unregistered
 * players (with register code) and registered players without an active
 * session (with login DM) and lets them in only after Discord verification.
 */
public class PrejoinVerificationBungeeListener implements Listener {

    private static final String DEFAULT_EMOJI = "✅";

    private final DIApi api = MainController.getDIApi();
    private final DIUserDao userDao = MainController.getDILoginController().getDIUserDao();

    @EventHandler(priority = EventPriority.LOW)
    public void onLogin(LoginEvent event) {
        String username = event.getConnection().getName();
        String ip = event.getConnection().getAddress() != null
                ? event.getConnection().getAddress().getAddress().getHostAddress()
                : "unknown";

        if (PrejoinCache.consumeVerified(username))
            return;

        if (userDao.contains(username)) {
            handleRegistered(event, username, ip);
        } else {
            handleUnregistered(event, username, ip);
        }
    }

    private void handleUnregistered(LoginEvent event, String username, String ip) {
        if (!PrejoinCache.tryAcquire(ip)) {
            denyEvent(event, LangController.getString(username, "prejoin_rate_limited"));
            return;
        }

        long ttlMillis = ttlMinutes() * 60_000L;
        String code = CodeGenerator.getCode(8, api);
        PrejoinCache.addPendingRegister(code, username, ttlMillis);

        String message = LangController.getString(username, "prejoin_register_kick_message")
                .replace("%code%", code)
                .replace("%ttl_min%", String.valueOf(ttlMinutes()));

        denyEvent(event, message);
    }

    private void handleRegistered(LoginEvent event, String username, String ip) {
        if (sessionValid(username, ip))
            return;

        if (!PrejoinCache.tryAcquire(ip)) {
            denyEvent(event, LangController.getString(username, "prejoin_rate_limited"));
            return;
        }

        if (PrejoinCache.hasPendingLoginFor(username)) {
            denyEvent(event, LangController.getString(username, "prejoin_login_kick_pending"));
            return;
        }

        Optional<User> discordUserOpt = userDao.get(username).flatMap(DIUser::getPlayerDiscord);
        Optional<JDA> jdaOpt = api.getCoreController().getDiscordApi();
        if (!discordUserOpt.isPresent() || !jdaOpt.isPresent()) {
            denyEvent(event, LangController.getString(username, "prejoin_login_kick_unavailable"));
            return;
        }

        sendLoginDm(jdaOpt.get(), discordUserOpt.get(), username);

        denyEvent(event, LangController.getString(username, "prejoin_login_kick_message"));
    }

    private void sendLoginDm(JDA jda, User discordUser, String username) {
        long graceMillis = graceMillis();
        long ttlMillis = Math.max(60_000L, graceMillis * 5L);
        String emoji = configuredEmoji();
        MessageEmbed embed = new EmbedBuilder()
                .setTitle(LangController.getString(username, "prejoin_login_dm_title"))
                .setDescription(LangController.getString(username, "prejoin_login_dm_desc"))
                .build();

        discordUser.openPrivateChannel().queue(channel ->
                channel.sendMessageEmbeds(embed).queue(message -> {
                    PrejoinCache.addPendingLogin(message.getIdLong(), username, discordUser.getIdLong(), ttlMillis);
                    message.addReaction(Emoji.fromFormatted(emoji)).queue(null,
                            err -> api.getInternalController().getLogger()
                                    .warning("Failed to add reaction to login DM: " + err.getMessage()));
                }, err -> api.getInternalController().getLogger()
                        .warning("Failed to send login DM to " + username + ": " + err.getMessage())),
                err -> api.getInternalController().getLogger()
                        .warning("Failed to open private channel for " + username + ": " + err.getMessage()));
    }

    private void denyEvent(LoginEvent event, String message) {
        event.setCancelled(true);
        event.setCancelReason(TextComponent.fromLegacyText(message));
    }

    private boolean sessionValid(String username, String ip) {
        return MainController.getDILoginController().isSessionEnabled()
                && UserSessionCache.isValid(username, ip);
    }

    private int ttlMinutes() {
        try {
            if (api.getInternalController().getConfigManager().contains("prejoin_verification_code_ttl_min"))
                return Math.max(1, api.getInternalController().getConfigManager()
                        .getInt("prejoin_verification_code_ttl_min"));
        } catch (Exception ignored) {
            // fallback
        }
        return 10;
    }

    private long graceMillis() {
        try {
            if (api.getInternalController().getConfigManager().contains("prejoin_verification_grace_sec"))
                return Math.max(10L, api.getInternalController().getConfigManager()
                        .getInt("prejoin_verification_grace_sec")) * 1000L;
        } catch (Exception ignored) {
            // fallback
        }
        return 60_000L;
    }

    private String configuredEmoji() {
        try {
            if (api.getInternalController().getConfigManager().contains("discord_embed_emoji"))
                return api.getInternalController().getConfigManager().getString("discord_embed_emoji");
        } catch (Exception ignored) {
            // fallback
        }
        return DEFAULT_EMOJI;
    }
}
