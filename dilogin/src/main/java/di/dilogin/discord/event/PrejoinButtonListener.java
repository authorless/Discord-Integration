package di.dilogin.discord.event;

import java.util.Optional;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.LangController;
import di.dilogin.minecraft.cache.PrejoinCache;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Handles button interactions on prejoin login DMs. The flow:
 * <ul>
 *   <li>{@code dilogin:prejoin:approve:<player>} → mark verified, allow next join</li>
 *   <li>{@code dilogin:prejoin:deny:<player>} → drop the pending login, log it</li>
 * </ul>
 */
public class PrejoinButtonListener extends ListenerAdapter {

    private static final String APPROVE_PREFIX = "dilogin:prejoin:approve:";
    private static final String DENY_PREFIX = "dilogin:prejoin:deny:";

    private final DIApi api = MainController.getDIApi();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id == null) return;

        boolean approve = id.startsWith(APPROVE_PREFIX);
        boolean deny = id.startsWith(DENY_PREFIX);
        if (!approve && !deny) return;

        Optional<PrejoinCache.PendingLogin> opt = PrejoinCache.consumePendingLogin(event.getMessageIdLong());
        if (!opt.isPresent()) {
            event.reply(LangController.getString("prejoin_login_dm_expired"))
                    .setEphemeral(true).queue();
            return;
        }

        PrejoinCache.PendingLogin pending = opt.get();

        if (pending.discordUserId != event.getUser().getIdLong()) {
            // Not the original recipient — restore entry and reject.
            PrejoinCache.addPendingLogin(event.getMessageIdLong(), pending.playerName, pending.discordUserId,
                    Math.max(0L, pending.expiry - System.currentTimeMillis()));
            event.reply(LangController.getString("prejoin_login_dm_not_owner"))
                    .setEphemeral(true).queue();
            return;
        }

        if (approve) {
            PrejoinCache.markVerified(pending.playerName, graceMillis());
            event.editButton(event.getButton().asDisabled()).queue();
            event.getHook().sendMessage(LangController.getString(pending.playerName, "prejoin_login_dm_confirmed"))
                    .setEphemeral(true).queue();
        } else {
            event.editButton(event.getButton().asDisabled()).queue();
            event.getHook().sendMessage(LangController.getString(pending.playerName, "prejoin_login_dm_denied"))
                    .setEphemeral(true).queue();
            api.getInternalController().getLogger().warning(
                    "Player " + pending.playerName + " denied a prejoin login attempt via Discord DM.");
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
