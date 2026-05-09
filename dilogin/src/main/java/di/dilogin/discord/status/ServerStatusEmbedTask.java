package di.dilogin.discord.status;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import di.dicore.api.DIApi;
import di.dilogin.controller.MainController;
import di.dilogin.controller.file.LangController;
import di.internal.utils.Util;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Background task that maintains a single embed in a configured Discord
 * channel reflecting live server status. Creates the message on first tick,
 * caches its id to disk and edits it on every subsequent tick to avoid
 * burning rate limit on fresh sends.
 */
public final class ServerStatusEmbedTask implements Runnable {

    private static final String STATE_FILE = "status_embed.id";

    private final DIApi api;
    private final ServerSnapshotProvider snapshot;
    private final File dataFolder;
    private final AtomicLong cachedMessageId = new AtomicLong(-1);

    public ServerStatusEmbedTask(DIApi api, ServerSnapshotProvider snapshot, File dataFolder) {
        this.api = api;
        this.snapshot = snapshot;
        this.dataFolder = dataFolder;
        this.cachedMessageId.set(loadStoredMessageId());
    }

    @Override
    public void run() {
        try {
            tick();
        } catch (Throwable t) {
            api.getInternalController().getLogger()
                    .warning("Status embed tick failed: " + t.getMessage());
        }
    }

    private void tick() {
        if (!isEnabled())
            return;

        Long channelId = configuredChannel();
        if (channelId == null)
            return;

        TextChannel channel = api.getCoreController().getDiscordApi()
                .map(jda -> jda.getTextChannelById(channelId)).orElse(null);
        if (channel == null)
            return;

        MessageEmbed embed = buildEmbed();
        long current = cachedMessageId.get();
        if (current > 0) {
            channel.editMessageEmbedsById(current, embed).queue(
                    success -> { /* edited */ },
                    err -> {
                        // message likely deleted — recreate
                        cachedMessageId.set(-1);
                        clearStoredMessageId();
                        sendNew(channel, embed);
                    });
        } else {
            sendNew(channel, embed);
        }
    }

    private void sendNew(TextChannel channel, MessageEmbed embed) {
        channel.sendMessageEmbeds(embed).queue(message -> {
            cachedMessageId.set(message.getIdLong());
            persistMessageId(message.getIdLong());
        }, err -> api.getInternalController().getLogger()
                .warning("Failed to create status embed: " + err.getMessage()));
    }

    private MessageEmbed buildEmbed() {
        ServerSnapshotProvider.Snapshot s = snapshot.snapshot();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(LangController.getString("status_embed_title"))
                .setDescription(s.motd == null ? "" : s.motd)
                .addField(LangController.getString("status_embed_players_field"),
                        s.onlinePlayers + " / " + s.maxPlayers, true)
                .addField(LangController.getString("status_embed_tps_field"),
                        formatTps(s.tps), true)
                .addField(LangController.getString("status_embed_uptime_field"),
                        formatUptime(s.uptimeSeconds), true)
                .setColor(resolveColor())
                .setTimestamp(java.time.Instant.now());
        if (s.playerNamesPreview != null && !s.playerNamesPreview.isEmpty()) {
            eb.addField(LangController.getString("status_embed_online_list_field"),
                    s.playerNamesPreview, false);
        }
        return eb.build();
    }

    private boolean isEnabled() {
        try {
            return api.getInternalController().getConfigManager().contains("status_embed_enabled")
                    && api.getInternalController().getConfigManager().getBoolean("status_embed_enabled");
        } catch (Exception ignored) {
            return false;
        }
    }

    private Long configuredChannel() {
        try {
            return api.getInternalController().getConfigManager().getLong("status_embed_channel");
        } catch (Exception ignored) {
            return null;
        }
    }

    private Color resolveColor() {
        try {
            return Util.hex2Rgb(api.getInternalController().getConfigManager().getString("discord_embed_color"));
        } catch (Exception ignored) {
            return new Color(0x4287F5);
        }
    }

    private static String formatTps(double tps) {
        return String.format("%.2f", tps);
    }

    private static String formatUptime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%dh %02dm %02ds", h, m, s);
    }

    private long loadStoredMessageId() {
        File f = new File(dataFolder, STATE_FILE);
        if (!f.exists()) return -1L;
        try {
            String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            return Long.parseLong(raw);
        } catch (IOException | NumberFormatException ignored) {
            return -1L;
        }
    }

    private void persistMessageId(long id) {
        Path path = new File(dataFolder, STATE_FILE).toPath();
        try {
            Files.write(path, Long.toString(id).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // best effort
        }
    }

    private void clearStoredMessageId() {
        File f = new File(dataFolder, STATE_FILE);
        if (f.exists()) f.delete();
    }

    /** Provider abstraction so the task does not depend on Bukkit/Bungee directly. */
    public interface ServerSnapshotProvider {
        Snapshot snapshot();

        final class Snapshot {
            public final int onlinePlayers;
            public final int maxPlayers;
            public final String motd;
            public final double tps;
            public final long uptimeSeconds;
            public final String playerNamesPreview;

            public Snapshot(int onlinePlayers, int maxPlayers, String motd, double tps,
                            long uptimeSeconds, String playerNamesPreview) {
                this.onlinePlayers = onlinePlayers;
                this.maxPlayers = maxPlayers;
                this.motd = motd;
                this.tps = tps;
                this.uptimeSeconds = uptimeSeconds;
                this.playerNamesPreview = playerNamesPreview;
            }
        }
    }
}
