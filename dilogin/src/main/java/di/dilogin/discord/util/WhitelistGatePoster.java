package di.dilogin.discord.util;

import java.awt.Color;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.LangController;
import di.internal.utils.Util;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/**
 * Posts an embed with Approve/Deny buttons to the configured admin channel
 * when an unregistered player attempts to join. Admins handle linking from
 * Discord without the player needing a code.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WhitelistGatePoster {

    public static boolean isEnabled() {
        try {
            DIApi api = MainController.getDIApi();
            return api.getInternalController().getConfigManager().contains("whitelist_gate_enabled")
                    && api.getInternalController().getConfigManager().getBoolean("whitelist_gate_enabled");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void post(String playerName, String ip) {
        DIApi api = MainController.getDIApi();
        Long channelId = configuredChannel(api);
        if (channelId == null)
            return;

        JDA jda = api.getCoreController().getDiscordApi().orElse(null);
        if (jda == null)
            return;

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            api.getInternalController().getLogger()
                    .warning("whitelist_gate_channel " + channelId + " not found.");
            return;
        }

        MessageEmbed embed = new EmbedBuilder()
                .setTitle(LangController.getString("whitelist_gate_embed_title"))
                .setDescription(LangController.getString(playerName, "whitelist_gate_embed_desc")
                        .replace("%ip%", ip == null ? "?" : ip))
                .setColor(resolveColor(api))
                .setTimestamp(java.time.Instant.now())
                .build();

        Button approve = Button.success("dilogin:gate:approve:" + playerName,
                LangController.getString("whitelist_gate_button_approve"));
        Button deny = Button.danger("dilogin:gate:deny:" + playerName,
                LangController.getString("whitelist_gate_button_deny"));

        channel.sendMessageEmbeds(embed).addActionRow(approve, deny).queue(null,
                err -> api.getInternalController().getLogger()
                        .warning("Failed to post whitelist gate embed: " + err.getMessage()));
    }

    private static Long configuredChannel(DIApi api) {
        try {
            return api.getInternalController().getConfigManager().getLong("whitelist_gate_channel");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Color resolveColor(DIApi api) {
        try {
            return Util.hex2Rgb(api.getInternalController().getConfigManager().getString("discord_embed_color"));
        } catch (Exception ignored) {
            return new Color(0x4287F5);
        }
    }
}
