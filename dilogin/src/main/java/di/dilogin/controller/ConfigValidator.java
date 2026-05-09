package di.dilogin.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import di.dicore.api.DIApi;
import di.internal.controller.file.ConfigManager;

/**
 * Eager startup validator: lists every required config key in one pass and
 * reports the full set of missing entries before disabling the plugin.
 *
 * Avoids the death-by-thousand-cuts UX where each missing key crashes a
 * different code path on first use.
 */
public final class ConfigValidator {

    private ConfigValidator() {}

    private static final List<String> REQUIRED_KEYS = Arrays.asList(
            "channel",
            "register_time_until_kick",
            "register_max_discord_accounts",
            "register_code_length",
            "login_time_until_kick",
            "login_system_enabled",
            "register_optional_enabled",
            "register_by_nickname_enabled",
            "register_by_discordid_enabled",
            "register_by_discord_command_enabled",
            "messages_only_channel",
            "sessions",
            "session_time_min",
            "session_persist",
            "database",
            "syncro_enable",
            "syncro_role_enabled",
            "register_give_role_enabled",
            "register_required_role_enabled",
            "register_role_list_enabled",
            "discord_embed_emoji",
            "discord_embed_color"
    );

    /**
     * @return list of missing required keys ({@code emptyList} if config is OK).
     */
    public static List<String> missingKeys(DIApi api) {
        ConfigManager cm = api.getInternalController().getConfigManager();
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            if (!cm.contains(key))
                missing.add(key);
        }
        return missing;
    }

    /**
     * Logs missing keys (if any) at SEVERE level. Returns {@code true} when the
     * config is valid.
     */
    public static boolean validateAndLog(DIApi api, Logger logger) {
        List<String> missing = missingKeys(api);
        if (missing.isEmpty())
            return true;
        logger.severe("Configuration is invalid. Missing required keys (" + missing.size() + "):");
        for (String key : missing) {
            logger.severe("  - " + key);
        }
        logger.severe("Add the missing entries to config.yml or regenerate the file (after backing up).");
        return false;
    }
}
