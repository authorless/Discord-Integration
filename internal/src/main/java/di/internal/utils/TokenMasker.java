package di.internal.utils;

import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility for sanitising strings that may contain a Discord bot token before
 * they are logged or surfaced to the user.
 *
 * Discord bot tokens follow the structure {@code <id>.<timestamp>.<hmac>}, with
 * URL-safe base64 segments. Anything that matches that shape is replaced with a
 * fixed sentinel.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenMasker {

    /**
     * Bot token pattern as published by Discord. Captures ids of any reasonable
     * length so future changes do not silently bypass the masker.
     */
    private static final Pattern BOT_TOKEN = Pattern.compile(
            "[A-Za-z0-9_-]{20,40}\\.[A-Za-z0-9_-]{5,8}\\.[A-Za-z0-9_-]{20,}");

    private static final String SENTINEL = "[REDACTED_DISCORD_TOKEN]";

    public static String mask(String input) {
        if (input == null || input.isEmpty())
            return input;
        return BOT_TOKEN.matcher(input).replaceAll(SENTINEL);
    }

    public static String mask(Throwable t) {
        return t == null ? null : mask(t.getMessage());
    }
}
