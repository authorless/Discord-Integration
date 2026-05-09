package di.dilogin.controller.impl;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import di.dicore.api.DIApi;
import di.dilogin.controller.DiscordController;
import di.dilogin.controller.MainController;
import di.dilogin.dao.DIUserDao;
import di.dilogin.entity.DIUser;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

/**
 * {@DiscordController} implementation.
 */
public class DiscordControllerImpl implements DiscordController {
	
	private final static DIApi api = MainController.getDIApi();

	@Override
	public boolean userHasRole(String roleid, String player) {
		Optional<Member> optMember = getMember(player);
		if (!optMember.isPresent())
			return false;

		Member member = optMember.get();
		List<Role> roles = member.getRoles();

		return roles.stream().anyMatch(role -> role.getId().equals(roleid));
	}

	@Override
	public boolean serverHasRole(String roleid) {
		Guild guild = getGuild();
		Role role = guild.getRoleById(roleid);
		if (role == null) {
			String message = "Could not find ROL with id: " + roleid + ". Check the plugin settings to avoid problems.";
			api.getInternalController().getLogger().log(Level.SEVERE, message);
			return false;
		}
		return true;
	}

	@Override
	public void giveRole(String roleid, String player, String reason) {
		Optional<Member> optMember = getMember(player);
		if (!optMember.isPresent()) {
			api.getInternalController().getLogger().warning(
					"Cannot give role " + roleid + ": Discord member for player '" + player + "' not found.");
			return;
		}
		giveRole(roleid, player, optMember.get(), reason);
	}

	@Override
	public void giveRole(String roleid, String player, Member member, String reason) {
		Guild guild = getGuild();
		if (guild == null) {
			api.getInternalController().getLogger().severe("Cannot give role " + roleid + ": guild not available.");
			return;
		}
		Role role = guild.getRoleById(roleid);
		if (role == null) {
			api.getInternalController().getLogger().severe(
					"Cannot give role to " + member.getUser().getAsTag()
							+ ": role id '" + roleid + "' not found. Check config.yml.");
			return;
		}

		guild.addRoleToMember(member, role).queue(
				success -> api.getInternalController().getLogger().info(
						role.getName() + " role has been given to " + member.getUser().getAsTag() + ". Reason: " + reason),
				err -> api.getInternalController().getLogger().log(Level.SEVERE,
						"Could not give " + role.getName() + " role to " + member.getUser().getAsTag()
								+ ": " + err.getMessage()));
	}

	@Override
	public void removeRole(String roleid, String player, String reason) {
		Optional<Member> optMember = getMember(player);
		if (!optMember.isPresent())
			return;

		Member member = optMember.get();
		Guild guild = getGuild();
		if (guild == null) return;
		Role role = guild.getRoleById(roleid);
		if (role == null) {
			api.getInternalController().getLogger().log(Level.SEVERE,
					"Could not remove role " + roleid + " from " + member.getUser().getAsTag() + ": role not found.");
			return;
		}

		guild.removeRoleFromMember(member, role).queue(
				success -> api.getInternalController().getLogger().info(role.getName() + " role has been removed from "
						+ member.getUser().getAsTag() + ". Reason: " + reason),
				err -> api.getInternalController().getLogger().log(Level.SEVERE,
						"Could not remove " + role.getName() + " role from " + member.getUser().getAsTag()
								+ ": " + err.getMessage()));
	}

	@Override
	public boolean isWhiteListed(String player) {
		Optional<Role> optRole = requiredRole();
		if (optRole.isPresent()) {
			Role role = optRole.get();
			Optional<Member> member = getMember(player);
			if (member.isPresent()) {
				return member.get().getRoles().contains(role);
			} else {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isWhiteListed(String player, Member member) {
		Optional<Role> optRole = requiredRole();
		if (api.getInternalController().getConfigManager().getBoolean("register_required_role_enabled") && optRole.isPresent()) {
			Role role = optRole.get();
			return member.getRoles().contains(role);
		}
		return true;	
	}

	/**
	 * Check for required role to whitelist on config file.
	 *
	 * @return optional role.
	 */
	private Optional<Role> requiredRole() {
		Guild guild = getGuild();

		Optional<Long> optionalLong = api.getInternalController().getConfigManager()
				.getOptionalLong("register_required_role_id");

		if (!optionalLong.isPresent())
			return Optional.empty();

		Role role = guild.getRoleById(optionalLong.get());
		if (role == null)
			return Optional.empty();

		return Optional.of(role);
	}

	/**
	 * Get Discord Member from Minecraft name.
	 * 
	 * @param player Minecraft name.
	 * @return The member if is present on JDA.
	 */
	private Optional<Member> getMember(String player) {
		DIUserDao dao = MainController.getDILoginController().getDIUserDao();
		Guild guild = getGuild();

		Optional<DIUser> DIUserOpt = dao.get(player);
		if (!DIUserOpt.isPresent())
			return Optional.empty();

		Optional<User> discordUserOpt = DIUserOpt.get().getPlayerDiscord();
		if (!discordUserOpt.isPresent() || guild == null)
			return Optional.empty();

		String discordId = discordUserOpt.get().getId();
		Member cached = guild.getMemberById(discordId);
		if (cached != null)
			return Optional.of(cached);

		try {
			Member retrieved = guild.retrieveMemberById(discordId).submit().get(10, TimeUnit.SECONDS);
			return Optional.ofNullable(retrieved);
		} catch (TimeoutException | ExecutionException e) {
			api.getInternalController().getLogger().warning("Failed to find Discord member for " + player + ": " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			api.getInternalController().getLogger().warning("Interrupted while finding Discord member for " + player);
		}

		return Optional.empty();
	}

	/**
	 * 
	 * @return Discord main guild.
	 */
	private Guild getGuild() {
		Optional<JDA> jdaOpt = api.getCoreController().getDiscordApi();
		if (!jdaOpt.isPresent())
			return null;
		return jdaOpt.get().getGuildById(api.getCoreController().getBot().getServerId());
	}
}
