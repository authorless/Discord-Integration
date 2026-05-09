package di.dilogin.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import di.dicore.api.DIApi;
import di.internal.controller.file.ConfigAutoMerger;
import di.internal.controller.file.ConfigManager;

/**
 * Eager startup validator. Now best-effort: if keys are missing it auto-merges
 * the bundled defaults into the user's file (preserving custom values) and
 * reloads. Only fails the startup if keys are still missing after the merge.
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
     * Validate, auto-merging bundled defaults for any missing keys before
     * giving up. Returns {@code true} if config is OK (possibly after merge).
     */
    public static boolean validateAndLog(DIApi api, ClassLoader classLoader, File dataFolder, Logger logger) {
        List<String> missing = missingKeys(api);
        if (missing.isEmpty())
            return true;

        File userConfig = new File(dataFolder, "config.yml");
        if (userConfig.exists()) {
            ConfigManager cm = api.getInternalController().getConfigManager();
            ConfigAutoMerger.MergeResult result = ConfigAutoMerger.mergeMissing(
                    userConfig, "config.yml", classLoader, cm, logger);
            if (result.fileChanged) {
                missing = missingKeys(api);
                if (missing.isEmpty()) {
                    logger.info("All previously missing config keys were filled with bundled defaults.");
                    return true;
                }
            }
        }

        logger.severe("Configuration is invalid. Missing required keys (" + missing.size()
                + ") that could not be auto-merged:");
        for (String key : missing) {
            logger.severe("  - " + key);
        }
        logger.severe("Add the missing entries to config.yml manually.");
        return false;
    }
}
