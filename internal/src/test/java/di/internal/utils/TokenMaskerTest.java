package di.internal.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenMaskerTest {

    @Test
    void masksAStandardBotTokenAnywhereInText() {
        String token = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMg.AbCdEf.AbCdEfGhIjKlMnOpQrStUvWxYz0123";
        String input = "Bot login failed: token=" + token + " network ok";
        String masked = TokenMasker.mask(input);

        assertFalse(masked.contains(token));
        assertTrue(masked.contains("[REDACTED_DISCORD_TOKEN]"));
        assertTrue(masked.contains("network ok"));
    }

    @Test
    void leavesUnrelatedTextUnchanged() {
        String safe = "Just a normal log line with numbers 12345 and dots a.b.c";
        assertEquals(safe, TokenMasker.mask(safe));
    }

    @Test
    void handlesNullAndEmpty() {
        assertNull(TokenMasker.mask((String) null));
        assertEquals("", TokenMasker.mask(""));
    }

    @Test
    void masksFromThrowableMessage() {
        String token = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMg.AbCdEf.AbCdEfGhIjKlMnOpQrStUvWxYz0123";
        Throwable t = new RuntimeException("auth failure for " + token);
        String masked = TokenMasker.mask(t);
        assertFalse(masked.contains(token));
    }
}
