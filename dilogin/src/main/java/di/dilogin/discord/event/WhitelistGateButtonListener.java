package di.dilogin.discord.event;

import java.util.Optional;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.LangController;
import di.dilogin.dao.DIUserDao;
import di.dilogin.discord.util.WelcomeDmSender;
import di.dilogin.entity.DIUser;
import di.dilogin.minecraft.cache.PrejoinCache;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Handles admin Approve/Deny clicks on the whitelist gate embed posted to the
 * configured admin channel when an unregistered player tries to join.
 *
 * The clicking admin's Discord account is what gets linked on Approve, on the
 * assumption that the admin is acting on behalf of (or as) the player. For
 * stricter setups configure {@code whitelist_gate_link_to_player_dm} so the
 * link is created for the player's own Discord instead — but that requires
 * matching by IGN to a known Discord ID via a separate flow.
 */
public class WhitelistGateButtonListener extends ListenerAdapter {

    private static final String APPROVE_PREFIX = "dilogin:gate:approve:";
    private static final String DENY_PREFIX = "dilogin:gate:deny:";

    private final DIApi api = MainController.getDIApi();
    private final DIUserDao userDao = MainController.getDILoginController().getDIUserDao();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id == null) return;

        boolean approve = id.startsWith(APPROVE_PREFIX);
        boolean deny = id.startsWith(DENY_PREFIX);
        if (!approve && !deny) return;

        if (!hasAdminRole(event)) {
            event.reply(LangController.getString("whitelist_gate_no_permission"))
                    .setEphemeral(true).queue();
            return;
        }

        String playerName = id.substring((approve ? APPROVE_PREFIX : DENY_PREFIX).length());

        if (deny) {
            event.editButton(event.getButton().asDisabled()).queue();
            event.getHook().sendMessage(LangController.getString(playerName, "whitelist_gate_denied")
                            .replace("%admin%", event.getUser().getAsTag()))
                    .queue();
            return;
        }

        if (userDao.contains(playerName)) {
            event.reply(LangController.getString("register_already_exists"))
                    .setEphemeral(true).queue();
            return;
        }

        // The clicking admin's Discord account is linked to the player.
        // For finer control, an admin can /unregister and the player can re-register manually.
        User linkUser = event.getUser();

        userDao.add(new DIUser(playerName, Optional.of(linkUser)));
        PrejoinCache.markVerified(playerName, graceMillis());
        WelcomeDmSender.greetIfFirstTime(linkUser, playerName);

        event.editButton(event.getButton().asDisabled()).queue();
        event.getHook().sendMessage(LangController.getString(playerName, "whitelist_gate_approved")
                        .replace("%admin%", event.getUser().getAsTag())
                        .replace("%minecraft_username%", playerName))
                .queue();

        api.getInternalController().getLogger().info(
                "Whitelist gate: " + event.getUser().getAsTag() + " approved player " + playerName
                        + " (linked to " + linkUser.getAsTag() + ").");
    }

    private boolean hasAdminRole(ButtonInteractionEvent event) {
        try {
            String roleId = api.getInternalController().getConfigManager()
                    .getString("whitelist_gate_admin_role");
            if (roleId == null || roleId.isEmpty() || "0".equals(roleId))
                return event.getMember() != null && event.getMember().hasPermission(Permission.MANAGE_SERVER);
            return event.getMember() != null && event.getMember().getRoles().stream()
                    .anyMatch(r -> r.getId().equals(roleId));
        } catch (Exception ignored) {
            return false;
        }
    }

    private long graceMillis() {
        try {
            if (api.getInternalController().getConfigManager().contains("prejoin_verification_grace_sec"))
                return Math.max(10L, api.getInternalController().getConfigManager()
                        .getInt("prejoin_verification_grace_sec")) * 1000L;
        } catch (Exception ignored) {
            // fallback
        }
        return 60_000L;
    }
}
