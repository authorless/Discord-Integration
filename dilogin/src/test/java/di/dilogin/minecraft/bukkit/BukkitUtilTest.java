package di.dilogin.minecraft.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BukkitUtilTest {

    @Test
    void parsesPaperVersionString() {
        assertEquals(20, BukkitUtil.getServerVersion("git-Paper-196 (MC: 1.20.1)"));
        assertEquals(21, BukkitUtil.getServerVersion("Paper 1.21.4"));
        assertEquals(8, BukkitUtil.getServerVersion("git-Spigot-abc (MC: 1.8.8)"));
    }

    @Test
    void picksHighestVersionWhenMultiplePresent() {
        // some servers include API version + impl version
        assertEquals(21, BukkitUtil.getServerVersion("Implementing 1.20-API on 1.21.1"));
    }

    @Test
    void unknownStringFallsBackToModern() {
        assertTrue(BukkitUtil.getServerVersion("???") >= 16);
        assertTrue(BukkitUtil.getServerVersion(null) >= 16);
    }
}
