package di.dilogin.discord.command;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.CommandAliasController;
import di.dilogin.controller.file.LangController;
import di.dilogin.dao.DIUserDao;
import di.dilogin.entity.DIUser;
import di.dilogin.minecraft.cache.PrejoinCache;
import di.internal.entity.DiscordCommand;
import di.internal.entity.DiscordSlashCommand;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * Discord command used by the prejoin verification flow.
 * Player runs {@code +confirm <code>} (or alias) on Discord with the code
 * shown in their kick message. On success the player is registered in the DB
 * and marked as verified so the next join attempt succeeds.
 */
public class PrejoinConfirmDiscordCommand implements DiscordCommand, DiscordSlashCommand {

    private final DIUserDao userDao = MainController.getDILoginController().getDIUserDao();
    private final DIApi api = MainController.getDIApi();

    @Override
    public void execute(String message, MessageReceivedEvent event) {
        runConfirm(event.getAuthor(), message, msg -> replyTemp(event, msg));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();
        OptionMapping codeOpt = event.getOption("code");
        String code = codeOpt == null ? "" : codeOpt.getAsString();
        runConfirm(event.getUser(), code,
                msg -> event.getHook().sendMessage(msg).setEphemeral(true).queue());
    }

    private void runConfirm(User discordUser, String message, Consumer<String> reply) {
        String code = message == null ? "" : message.trim();

        if (code.isEmpty()) {
            reply.accept(LangController.getString("prejoin_confirm_missing_code"));
            return;
        }

        if (userDao.containsDiscordId(discordUser.getIdLong())
                && userDao.getDiscordUserAccounts(discordUser.getIdLong())
                        >= api.getInternalController().getConfigManager().getInt("register_max_discord_accounts")) {
            reply.accept(LangController.getString("register_max_accounts"));
            return;
        }

        Optional<PrejoinCache.PendingRegister> pendingOpt = PrejoinCache.consumePendingRegister(code);
        if (!pendingOpt.isPresent()) {
            reply.accept(LangController.getString("prejoin_confirm_invalid_code"));
            return;
        }

        String playerName = pendingOpt.get().playerName;

        if (userDao.contains(playerName)) {
            reply.accept(LangController.getString("register_already_exists"));
            return;
        }

        userDao.add(new DIUser(playerName, Optional.of(discordUser)));
        PrejoinCache.markVerified(playerName, graceMillis());

        reply.accept(LangController.getString(discordUser, playerName, "prejoin_confirm_success"));
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
