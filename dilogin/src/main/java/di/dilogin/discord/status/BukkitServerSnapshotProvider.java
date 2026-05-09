package di.dilogin.discord.status;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class BukkitServerSnapshotProvider implements ServerStatusEmbedTask.ServerSnapshotProvider {

    private static final int PREVIEW_LIMIT = 20;

    @Override
    public Snapshot snapshot() {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String motd = stripColors(Bukkit.getMotd());
        double tps = readTps();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
        String preview = Bukkit.getOnlinePlayers().stream()
                .limit(PREVIEW_LIMIT)
                .map(Player::getName)
                .collect(Collectors.joining(", "));
        if (online > PREVIEW_LIMIT) {
            preview = preview + " ... (+" + (online - PREVIEW_LIMIT) + ")";
        }
        return new Snapshot(online, max, motd, tps, uptime, preview);
    }

    /** TPS is exposed by Paper/Spigot via reflection-friendly Server methods. */
    private static double readTps() {
        try {
            Object server = Bukkit.getServer();
            Method m = server.getClass().getMethod("getTPS");
            double[] tps = (double[]) m.invoke(server);
            if (tps != null && tps.length > 0)
                return Math.min(20.0, tps[0]);
        } catch (Throwable ignored) {
            // older API: no TPS available
        }
        return 20.0;
    }

    private static String stripColors(String input) {
        if (input == null) return null;
        return input.replaceAll("§[0-9a-fk-or]", "");
    }
}
