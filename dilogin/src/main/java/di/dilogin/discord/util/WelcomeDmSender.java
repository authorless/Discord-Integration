package di.dilogin.discord.util;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.LangController;
import di.internal.utils.Util;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/**
 * Sends a one-shot welcome DM the first time a Discord account is linked to
 * an in-game player. Useful for surfacing rules, channel pointers and basic
 * commands right after registration.
 *
 * The "already greeted" set lives in memory only — restarts may re-send to a
 * player who registered just before the restart. Acceptable for an onboarding
 * touchpoint; persisting it would need a schema change.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WelcomeDmSender {

    private static final Set<Long> alreadyGreeted = new HashSet<>();

    public static synchronized void greetIfFirstTime(User discordUser, String playerName) {
        DIApi api = MainController.getDIApi();
        if (!isEnabled(api))
            return;

        long id = discordUser.getIdLong();
        if (!alreadyGreeted.add(id))
            return; // already greeted in this JVM lifetime

        MessageEmbed embed = buildEmbed(api, discordUser, playerName);
        MessageCreateData payload = new MessageCreateBuilder().addEmbeds(embed).build();

        DiscordDmCoordinator.sendOrEdit(discordUser, payload, embed,
                msg -> { /* sent */ },
                err -> api.getInternalController().getLogger()
                        .warning("Failed to send welcome DM to " + playerName + ": " + err.getMessage()));
    }

    private static boolean isEnabled(DIApi api) {
        try {
            return api.getInternalController().getConfigManager().contains("welcome_dm_enabled")
                    && api.getInternalController().getConfigManager().getBoolean("welcome_dm_enabled");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static MessageEmbed buildEmbed(DIApi api, User discordUser, String playerName) {
        String title = LangController.getString(discordUser, playerName, "welcome_dm_title");
        String body = LangController.getString(discordUser, playerName, "welcome_dm_body");

        EmbedBuilder eb = new EmbedBuilder().setTitle(title).setDescription(body);
        try {
            String hex = api.getInternalController().getConfigManager().getString("discord_embed_color");
            eb.setColor(Util.hex2Rgb(hex));
        } catch (Exception ignored) {
            eb.setColor(new Color(0x4287F5));
        }
        return eb.build();
    }
}
