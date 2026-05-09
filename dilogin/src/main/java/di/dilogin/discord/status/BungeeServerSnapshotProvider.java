package di.dilogin.discord.status;

import java.lang.management.ManagementFactory;
import java.util.stream.Collectors;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public final class BungeeServerSnapshotProvider implements ServerStatusEmbedTask.ServerSnapshotProvider {

    private static final int PREVIEW_LIMIT = 20;

    @Override
    public Snapshot snapshot() {
        ProxyServer proxy = ProxyServer.getInstance();
        int online = proxy.getOnlineCount();
        int max = proxy.getConfigurationAdapter().getInt("player_limit", -1);
        String motd = null;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
        String preview = proxy.getPlayers().stream()
                .limit(PREVIEW_LIMIT)
                .map(ProxiedPlayer::getName)
                .collect(Collectors.joining(", "));
        if (online > PREVIEW_LIMIT) {
            preview = preview + " ... (+" + (online - PREVIEW_LIMIT) + ")";
        }
        // Bungee has no TPS — report 20.0 as a placeholder so the field stays consistent.
        return new Snapshot(online, max, motd, 20.0, uptime, preview);
    }
}
