package di.dilogin.minecraft.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrejoinCacheTest {

    @BeforeEach
    void resetCaches() throws Exception {
        clearStaticMap("pendingRegisters");
        clearStaticMap("verifiedPlayers");
        clearStaticMap("rateLimits");
        clearStaticMap("pendingLogins");
        clearStaticMap("pendingAuthme");
    }

    @SuppressWarnings("rawtypes")
    private void clearStaticMap(String name) throws Exception {
        Field f = PrejoinCache.class.getDeclaredField(name);
        f.setAccessible(true);
        ((Map) f.get(null)).clear();
    }

    @Test
    void consumePendingRegisterReturnsAndRemovesEntry() {
        PrejoinCache.addPendingRegister("CODE1", "alice", 60_000);

        assertTrue(PrejoinCache.consumePendingRegister("CODE1").isPresent());
        assertFalse(PrejoinCache.consumePendingRegister("CODE1").isPresent(),
                "second consume must miss");
    }

    @Test
    void expiredCodesAreNotReturned() {
        PrejoinCache.addPendingRegister("OLD", "alice", -1L); // already expired
        assertFalse(PrejoinCache.consumePendingRegister("OLD").isPresent());
    }

    @Test
    void verifiedFlagIsConsumedOnce() {
        PrejoinCache.markVerified("alice", 60_000);

        assertTrue(PrejoinCache.consumeVerified("alice"));
        assertFalse(PrejoinCache.consumeVerified("alice"));
    }

    @Test
    void rateLimitBlocksAfterThreshold() {
        String ip = "1.2.3.4";
        for (int i = 0; i < 5; i++) {
            assertTrue(PrejoinCache.tryAcquire(ip), "first 5 attempts allowed");
        }
        assertFalse(PrejoinCache.tryAcquire(ip), "6th attempt blocked within window");
    }

    @Test
    void rateLimitIsScopedPerIp() {
        for (int i = 0; i < 5; i++) PrejoinCache.tryAcquire("attacker");
        assertTrue(PrejoinCache.tryAcquire("legit-user"));
    }

    @Test
    void purgeExpiredRemovesStaleEntries() {
        PrejoinCache.addPendingRegister("EXPIRED", "ghost", -1L);
        PrejoinCache.purgeExpired();
        assertFalse(PrejoinCache.consumePendingRegister("EXPIRED").isPresent());
    }

    @Test
    void pendingLoginRoundTripByMessageId() {
        PrejoinCache.addPendingLogin(42L, "alice", 1234L, 60_000);

        assertTrue(PrejoinCache.hasPendingLoginFor("alice"));
        PrejoinCache.PendingLogin pl = PrejoinCache.consumePendingLogin(42L).orElseThrow(AssertionError::new);
        assertTrue(pl.playerName.equals("alice") && pl.discordUserId == 1234L);
        assertFalse(PrejoinCache.consumePendingLogin(42L).isPresent());
        assertFalse(PrejoinCache.hasPendingLoginFor("alice"));
    }

    @Test
    void expiredPendingLoginIsRejected() {
        PrejoinCache.addPendingLogin(99L, "alice", 1L, -1L);
        assertFalse(PrejoinCache.consumePendingLogin(99L).isPresent());
    }

    @Test
    void pendingAuthmeRoundTrip() {
        PrejoinCache.addPendingAuthme("alice", "secret", 60_000);
        assertTrue(PrejoinCache.consumePendingAuthme("alice").isPresent());
        assertFalse(PrejoinCache.consumePendingAuthme("alice").isPresent());
    }
}
