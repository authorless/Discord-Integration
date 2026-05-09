package di.dilogin.discord.command;

import java.time.Duration;
import java.util.Optional;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.CommandAliasController;
import di.dilogin.controller.file.LangController;
import di.dilogin.dao.DIUserDao;
import di.dilogin.entity.DIUser;
import di.dilogin.minecraft.cache.PrejoinCache;
import di.internal.entity.DiscordCommand;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * Discord command used by the prejoin verification flow.
 * Player runs {@code +confirm <code>} (or alias) on Discord with the code
 * shown in their kick message. On success the player is registered in the DB
 * and marked as verified so the next join attempt succeeds.
 */
public class PrejoinConfirmDiscordCommand implements DiscordCommand {

    private final DIUserDao userDao = MainController.getDILoginController().getDIUserDao();
    private final DIApi api = MainController.getDIApi();

    @Override
    public void execute(String message, MessageReceivedEvent event) {
        User discordUser = event.getAuthor();
        String code = message == null ? "" : message.trim();

        if (code.isEmpty()) {
            replyTemp(event, LangController.getString("prejoin_confirm_missing_code"));
            return;
        }

        if (userDao.containsDiscordId(discordUser.getIdLong())
                && userDao.getDiscordUserAccounts(discordUser.getIdLong())
                        >= api.getInternalController().getConfigManager().getInt("register_max_discord_accounts")) {
            replyTemp(event, LangController.getString("register_max_accounts"));
            return;
        }

        Optional<PrejoinCache.PendingRegister> pendingOpt = PrejoinCache.consumePendingRegister(code);
        if (!pendingOpt.isPresent()) {
            replyTemp(event, LangController.getString("prejoin_confirm_invalid_code"));
            return;
        }

        String playerName = pendingOpt.get().playerName;

        if (userDao.contains(playerName)) {
            replyTemp(event, LangController.getString("register_already_exists"));
            return;
        }

        userDao.add(new DIUser(playerName, Optional.of(discordUser)));
        PrejoinCache.markVerified(playerName, graceMillis());

        replyTemp(event, LangController.getString(discordUser, playerName, "prejoin_confirm_success"));
    }

    private long graceMillis() {
        try {
            if (api.getInternalController().getConfigManager().contains("prejoin_verification_grace_sec")) {
                return Math.max(10L,
                        api.getInternalController().getConfigManager().getInt("prejoin_verification_grace_sec")) * 1000L;
            }
        } catch (Exception ignored) {
            // fallback
        }
        return 60_000L;
    }

    private void replyTemp(MessageReceivedEvent event, String text) {
        event.getChannel().sendMessage(text)
                .delay(Duration.ofSeconds(20))
                .flatMap(Message::delete)
                .queue(null, err -> { /* ignore deletion failures */ });
    }

    @Override
    public String getAlias() {
        return CommandAliasController.getAlias("prejoin_confirm_command");
    }
}
