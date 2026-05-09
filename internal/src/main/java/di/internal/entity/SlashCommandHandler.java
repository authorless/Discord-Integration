package di.internal.entity;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Represents the Discord slash command handler.
 */
public class SlashCommandHandler extends ListenerAdapter {

	/**
	 * Slash command list.
	 */
	private final HashMap<String, DiscordSlashCommand> commands = new HashMap<>();

	/**
	 * Add new slash command to the list.
	 * 
	 * @param command The slash command to add.
	 */
	public void registerCommand(DiscordSlashCommand command) {
		commands.put(command.getAlias(), command);
	}

	/**
	 * Main constructor of SlashCommandHandler.
	 */
	public SlashCommandHandler() {
		//No args required
	}

	/**
	 * Execute slash command
	 * 
	 * @param event Main event.
	 */
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (event.getUser().isBot())
			return;
		
		String command = event.getName();

		DiscordSlashCommand handler = commands.get(command);
		if (handler == null) {
			Logger.getLogger(SlashCommandHandler.class.getName())
					.warning("No handler registered for slash command '" + command + "'");
			return;
		}
		try {
			handler.execute(event);
		} catch (Exception e) {
			Logger.getLogger(SlashCommandHandler.class.getName())
					.log(Level.SEVERE, "Error executing slash command '" + command + "'", e);
		}
	}
}