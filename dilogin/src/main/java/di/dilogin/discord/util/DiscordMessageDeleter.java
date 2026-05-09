package di.dilogin.discord.util;

import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;

/**
 * Utility class for deleting Discord messages with retry mechanism.
 * Uses JDA's internal scheduler so retries cancel cleanly on JDA shutdown.
 */
public class DiscordMessageDeleter {

    private static final int RETRY_DELAY_SECONDS = 30;
    private static final int MAX_RETRIES = 3;

    /**
     * Deletes the specified message with retry mechanism.
     *
     * @param initialDelay The initial delay before the first attempt (in seconds).
     * @param message      The message to delete.
     */
    public static void deleteMessage(int initialDelay, Message message) {
        if (message == null)
            return;
        scheduleRetry(message, initialDelay, MAX_RETRIES);
    }

    private static void scheduleRetry(Message message, int delay, int retries) {
        if (!isDeletable(message))
            return;

        message.delete().queueAfter(delay, TimeUnit.SECONDS,
                success -> { /* no-op */ },
                failure -> {
                    if (retries > 0)
                        scheduleRetry(message, RETRY_DELAY_SECONDS, retries - 1);
                });
    }

    private static boolean isDeletable(Message message) {
        return !message.isFromType(ChannelType.PRIVATE) || message.getAuthor().isBot();
    }
}
