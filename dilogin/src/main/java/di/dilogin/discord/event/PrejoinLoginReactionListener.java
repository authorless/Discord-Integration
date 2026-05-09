package di.dilogin.discord.event;

import java.time.Duration;
import java.util.Optional;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.LangController;
import di.dilogin.minecraft.cache.PrejoinCache;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Listens for reactions on the login DMs sent during the prejoin verification
 * flow. When the original recipient reacts to one of these messages we mark
 * the player as verified so the next reconnect succeeds.
 */
public class PrejoinLoginReactionListener extends ListenerAdapter {

    private final DIApi api = MainController.getDIApi();

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot())
            return;

        Optional<PrejoinCache.PendingLogin> opt = PrejoinCache.consumePendingLogin(event.getMessageIdLong());
        if (!opt.isPresent())
            return;

        PrejoinCache.PendingLogin pending = opt.get();

        if (pending.discordUserId != event.getUserIdLong()) {
            // Not the player; restore the entry and ignore.
            PrejoinCache.addPendingLogin(event.getMessageIdLong(), pending.playerName, pending.discordUserId,
                    Math.max(0L, pending.expiry - System.currentTimeMillis()));
            return;
        }

        long graceMillis = graceMillis();
        PrejoinCache.markVerified(pending.playerName, graceMillis);

        if (event.getChannel() != null) {
            String confirmation = LangController.getString(pending.playerName, "prejoin_login_dm_confirmed");
            event.getChannel().sendMessage(confirmation)
                    .delay(Duration.ofSeconds(20))
                    .flatMap(Message::delete)
                    .queue(null, err -> {
                        // ignore
                    });
        }
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
}
