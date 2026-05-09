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

    public static void addPendingRegister(String code, String playerName, long ttlMillis) {
        pendingRegisters.put(code, new PendingRegister(playerName, System.currentTimeMillis() + ttlMillis));
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

    public static void purgeExpired() {
        long now = System.currentTimeMillis();
        pendingRegisters.entrySet().removeIf(e -> e.getValue().expiry < now);
        verifiedPlayers.entrySet().removeIf(e -> e.getValue() < now);
    }

    public static class PendingRegister {
        public final String playerName;
        public final long expiry;

        PendingRegister(String playerName, long expiry) {
            this.playerName = playerName;
            this.expiry = expiry;
        }
    }
}
