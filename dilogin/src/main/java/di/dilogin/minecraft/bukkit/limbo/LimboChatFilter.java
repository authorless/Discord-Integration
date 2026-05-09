package di.dilogin.minecraft.bukkit.limbo;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Removes any limbo player from the recipient set of every chat message so
 * they do not see live chat while waiting for Discord verification.
 *
 * The event is the legacy chat one — it is still delivered on Paper and
 * Spigot and is handled before AdventureChatEvent fires on modern Paper, so
 * intercepting it covers both paths.
 */
@SuppressWarnings("deprecation")
public class LimboChatFilter implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        event.getRecipients().removeIf(LimboChatFilter::shouldHideFromRecipient);
    }

    private static boolean shouldHideFromRecipient(Player recipient) {
        return LimboController.isInLimbo(recipient);
    }
}
