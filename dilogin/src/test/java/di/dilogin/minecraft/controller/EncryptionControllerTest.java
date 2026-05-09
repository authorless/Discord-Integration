package di.dilogin.minecraft.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EncryptionControllerTest {

    @Test
    void sameInputProducesSameHash() {
        String a = EncryptionController.encrypt("192.168.0.1");
        String b = EncryptionController.encrypt("192.168.0.1");
        assertEquals(a, b);
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        String a = EncryptionController.encrypt("192.168.0.1");
        String b = EncryptionController.encrypt("10.0.0.1");
        assertNotEquals(a, b);
    }

    @Test
    void hashLengthIsSha256Hex() {
        String hash = EncryptionController.encrypt("anything");
        assertEquals(64, hash.length());
        // hex only
        assertEquals(hash, hash.toLowerCase());
    }

    @Test
    void nullInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> EncryptionController.encrypt(null));
    }
}
