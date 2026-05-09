package di.dicore.event;

import di.internal.controller.CoreController;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.entities.Activity;

/**
 * Resolves the configured Discord {@link Activity} type and renders the
 * placeholder-based status content. Centralised so Bukkit and Bungee
 * status drivers stay in lockstep.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BotActivityBuilder {

    public static Activity build(CoreController controller, String renderedContent) {
        ActivityKind kind = resolveKind(controller);
        switch (kind) {
            case WATCHING:
                return Activity.watching(renderedContent);
            case LISTENING:
                return Activity.listening(renderedContent);
            case COMPETING:
                return Activity.competing(renderedContent);
            case STREAMING:
                String url = readString(controller, "status_streaming_url", "https://twitch.tv/");
                return Activity.streaming(renderedContent, url);
            case PLAYING:
            default:
                return Activity.playing(renderedContent);
        }
    }

    /**
     * Render the configured raw {@code status_content} replacing every
     * supported placeholder with live values.
     */
    public static String render(CoreController controller, String rawContent,
                                int onlinePlayers, int maxPlayers, String motd) {
        String serverName = readString(controller, "server_name", "");
        String safeMotd = motd == null ? "" : motd;
        return rawContent
                .replace("%minecraft_players%", String.valueOf(onlinePlayers))
                .replace("%minecraft_max_players%", maxPlayers > 0 ? String.valueOf(maxPlayers) : "?")
                .replace("%minecraft_motd%", safeMotd)
                .replace("%server_name%", serverName);
    }

    private static ActivityKind resolveKind(CoreController controller) {
        String raw = readString(controller, "status_activity_type", "PLAYING");
        try {
            return ActivityKind.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return ActivityKind.PLAYING;
        }
    }

    private static String readString(CoreController controller, String key, String fallback) {
        try {
            if (controller.getConfigManager().contains(key)) {
                String value = controller.getConfigManager().getString(key);
                if (value != null && !value.isEmpty())
                    return value;
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return fallback;
    }

    private enum ActivityKind {
        PLAYING, WATCHING, LISTENING, COMPETING, STREAMING
    }
}
