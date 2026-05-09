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
}
