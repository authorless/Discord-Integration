package di.dilogin.minecraft.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Caches for prejoin verification flow: tracks codes pending registration and
 * players already verified within a grace window so the next join attempt
 * succeeds.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PrejoinCache {

    /**
     * Pending register codes: code -> {playerName, expiryEpochMillis}.
     * The code is sent to the player on kick; they paste it into Discord to
     * complete registration.
     */
    private static final Map<String, PendingRegister> pendingRegisters = new ConcurrentHashMap<>();

    /**
     * Verified players: playerName -> expiryEpochMillis. While the entry is
     * valid the player is allowed to join without further verification.
     */
    private static final Map<String, Long> verifiedPlayers = new ConcurrentHashMap<>();

    /**
     * Pending login DMs (registered players awaiting Discord reaction):
     * messageId -> {playerName, expiryEpochMillis, discordUserId}.
     */
    private static final Map<Long, PendingLogin> pendingLogins = new ConcurrentHashMap<>();

    /**
     * Pending AuthMe registrations (player completed +confirm, AuthMe register
     * deferred until they actually join):
     * playerName -> {password, expiryEpochMillis}.
     */
    private static final Map<String, PendingAuthme> pendingAuthme = new ConcurrentHashMap<>();

    /**
     * Per-IP rate-limit window: ip -> {count, windowStartMillis}.
     * Prevents an attacker from spamming join attempts to fill the cache.
     */
    private static final Map<String, RateWindow> rateLimits = new ConcurrentHashMap<>();

    /**
     * Hard cap on total pending entries — if exceeded, new requests are rejected
     * regardless of IP throttle. Defends against distributed attempts.
     */
    private static final int MAX_PENDING_ENTRIES = 1000;
    private static final long RATE_WINDOW_MILLIS = 60_000L;
    private static final int RATE_MAX_PER_WINDOW = 5;

    public static void addPendingRegister(String code, String playerName, long ttlMillis) {
        pendingRegisters.put(code, new PendingRegister(playerName, System.currentTimeMillis() + ttlMillis));
    }

    /**
     * Check whether a new prejoin attempt from {@code ip} should be allowed.
     * Returns {@code false} if the IP exceeded {@value RATE_MAX_PER_WINDOW}
     * attempts in the last {@value RATE_WINDOW_MILLIS}ms or if the global
     * pending cap is exceeded.
     */
    public static boolean tryAcquire(String ip) {
        if (pendingRegisters.size() >= MAX_PENDING_ENTRIES)
            return false;
        long now = System.currentTimeMillis();
        RateWindow current = rateLimits.compute(ip, (k, w) -> {
            if (w == null || now - w.windowStart > RATE_WINDOW_MILLIS)
                return new RateWindow(now, 1);
            return new RateWindow(w.windowStart, w.count + 1);
        });
        return current.count <= RATE_MAX_PER_WINDOW;
    }

    public static Optional<PendingRegister> consumePendingRegister(String code) {
        PendingRegister pr = pendingRegisters.remove(code);
        if (pr == null || pr.expiry < System.currentTimeMillis())
            return Optional.empty();
        return Optional.of(pr);
    }

    public static Optional<String> findPlayerByPendingCode(String code) {
        PendingRegister pr = pendingRegisters.get(code);
        if (pr == null || pr.expiry < System.currentTimeMillis())
            return Optional.empty();
        return Optional.of(pr.playerName);
    }

    public static void markVerified(String playerName, long graceMillis) {
        verifiedPlayers.put(playerName, System.currentTimeMillis() + graceMillis);
    }

    /**
     * Atomically consume the verified flag: returns true once and removes the entry.
     */
    public static boolean consumeVerified(String playerName) {
        Long expiry = verifiedPlayers.remove(playerName);
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    public static void addPendingLogin(long messageId, String playerName, long discordUserId, long ttlMillis) {
        pendingLogins.put(messageId, new PendingLogin(playerName, discordUserId,
                System.currentTimeMillis() + ttlMillis));
    }

    public static Optional<PendingLogin> consumePendingLogin(long messageId) {
        PendingLogin pl = pendingLogins.remove(messageId);
        if (pl == null || pl.expiry < System.currentTimeMillis())
            return Optional.empty();
        return Optional.of(pl);
    }

    public static boolean hasPendingLoginFor(String playerName) {
        long now = System.currentTimeMillis();
        return pendingLogins.values().stream()
                .anyMatch(p -> p.playerName.equals(playerName) && p.expiry >= now);
    }

    public static void addPendingAuthme(String playerName, String password, long ttlMillis) {
        pendingAuthme.put(playerName, new PendingAuthme(password, System.currentTimeMillis() + ttlMillis));
    }

    public static Optional<String> consumePendingAuthme(String playerName) {
        PendingAuthme pa = pendingAuthme.remove(playerName);
        if (pa == null || pa.expiry < System.currentTimeMillis())
            return Optional.empty();
        return Optional.of(pa.password);
    }

    public static void purgeExpired() {
        long now = System.currentTimeMillis();
        pendingRegisters.entrySet().removeIf(e -> e.getValue().expiry < now);
        verifiedPlayers.entrySet().removeIf(e -> e.getValue() < now);
        pendingLogins.entrySet().removeIf(e -> e.getValue().expiry < now);
        pendingAuthme.entrySet().removeIf(e -> e.getValue().expiry < now);
        rateLimits.entrySet().removeIf(e -> now - e.getValue().windowStart > RATE_WINDOW_MILLIS);
    }

    public static class PendingRegister {
        public final String playerName;
        public final long expiry;

        PendingRegister(String playerName, long expiry) {
            this.playerName = playerName;
            this.expiry = expiry;
        }
    }

    public static class PendingLogin {
        public final String playerName;
        public final long discordUserId;
        public final long expiry;

        PendingLogin(String playerName, long discordUserId, long expiry) {
            this.playerName = playerName;
            this.discordUserId = discordUserId;
            this.expiry = expiry;
        }
    }

    private static final class PendingAuthme {
        final String password;
        final long expiry;

        PendingAuthme(String password, long expiry) {
            this.password = password;
            this.expiry = expiry;
        }
    }

    private static final class RateWindow {
        final long windowStart;
        final int count;

        RateWindow(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
