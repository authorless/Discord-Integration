package di.dilogin.discord.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/**
 * Centralised coordinator for Discord DMs.
 *
 * Discord enforces a per-recipient DM limit of 5 messages / 5s. When several
 * players reconnect at once the plugin can easily saturate that bucket and
 * legitimate messages get dropped. This coordinator throttles per-recipient,
 * coalesces duplicates within a short window, and prefers editing the existing
 * DM over sending a new one.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DiscordDmCoordinator {

    /** Minimum gap between two DMs to the same recipient. */
    private static final long MIN_GAP_MILLIS = 2_000L;

    private static final Map<Long, RecipientState> states = new ConcurrentHashMap<>();

    /**
     * Send an embed DM, coalescing if the same recipient was contacted within
     * {@value #MIN_GAP_MILLIS}ms — in that case the previous message is edited
     * to show the new embed instead of sending a new one.
     *
     * @param onSent receives the (possibly reused) message id; safe to register
     *               reactions or buttons on it.
     */
    public static void sendOrEdit(User recipient, MessageCreateData payload, MessageEmbed embed,
                                  Consumer<Message> onSent, Consumer<Throwable> onError) {
        long userId = recipient.getIdLong();
        long now = System.currentTimeMillis();

        RecipientState state = states.compute(userId, (k, existing) -> {
            if (existing == null)
                return new RecipientState(now);
            return new RecipientState(now, existing.lastMessage, existing.lastMessageAt);
        });

        if (state.lastMessage != null && now - state.lastMessageAt < MIN_GAP_MILLIS) {
            // Within the throttle window — edit the previous DM instead of sending a new one.
            state.lastMessage.editMessageEmbeds(embed).queue(edited -> {
                states.put(userId, new RecipientState(now, edited, now));
                onSent.accept(edited);
            }, err -> fallbackSend(recipient, payload, userId, now, onSent, onError));
            return;
        }

        recipient.openPrivateChannel().queue(channel ->
                send(channel, payload, userId, onSent, onError),
                err -> {
                    if (onError != null) onError.accept(err);
                });
    }

    private static void send(PrivateChannel channel, MessageCreateData payload, long userId,
                             Consumer<Message> onSent, Consumer<Throwable> onError) {
        channel.sendMessage(payload).queue(message -> {
            states.put(userId, new RecipientState(System.currentTimeMillis(), message, System.currentTimeMillis()));
            if (onSent != null) onSent.accept(message);
        }, err -> {
            if (onError != null) onError.accept(err);
        });
    }

    private static void fallbackSend(User recipient, MessageCreateData payload, long userId, long now,
                                     Consumer<Message> onSent, Consumer<Throwable> onError) {
        // Edit failed (e.g. message deleted) — fall through to sending fresh.
        recipient.openPrivateChannel().queue(channel ->
                send(channel, payload, userId, onSent, onError),
                err -> {
                    if (onError != null) onError.accept(err);
                });
    }

    /**
     * Drop tracking entries that are older than the throttle window. Should be
     * invoked periodically by a scheduler so the map does not grow unbounded.
     */
    public static void purgeStale() {
        long cutoff = System.currentTimeMillis() - 5L * 60_000L;
        states.entrySet().removeIf(e -> e.getValue().lastMessageAt < cutoff);
    }

    private static final class RecipientState {
        final long lastSeenAt;
        final Message lastMessage;
        final long lastMessageAt;

        RecipientState(long lastSeenAt) {
            this(lastSeenAt, null, 0L);
        }

        RecipientState(long lastSeenAt, Message lastMessage, long lastMessageAt) {
            this.lastSeenAt = lastSeenAt;
            this.lastMessage = lastMessage;
            this.lastMessageAt = lastMessageAt;
        }
    }
}
