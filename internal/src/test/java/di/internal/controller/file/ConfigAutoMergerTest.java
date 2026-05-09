package di.internal.controller.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigAutoMergerTest {

    private static final Logger LOG = Logger.getLogger("test");

    @Test
    void appendsMissingKeysWithCommentBlock(@TempDir Path tmp) throws IOException {
        // bundled defaults — written to a fake "resources" directory and exposed via URLClassLoader
        Path resourcesDir = tmp.resolve("resources");
        Files.createDirectories(resourcesDir);
        Files.write(resourcesDir.resolve("config.yml"), (
                "channel: CHANNEL\n" +
                        "\n" +
                        "#Section A\n" +
                        "old_key: 1\n" +
                        "\n" +
                        "#Newly added in this release\n" +
                        "#Multi-line comment.\n" +
                        "shiny_new_key: true\n").getBytes(StandardCharsets.UTF_8));

        URLClassLoader loader = new URLClassLoader(new URL[] { resourcesDir.toUri().toURL() });

        // user file — has the originals but not the new key
        Path userFile = tmp.resolve("config.yml");
        Files.write(userFile, (
                "channel: 12345\n" +
                        "\n" +
                        "#Section A\n" +
                        "old_key: 99\n").getBytes(StandardCharsets.UTF_8));

        ConfigAutoMerger.MergeResult result = ConfigAutoMerger.mergeMissing(
                userFile.toFile(), "config.yml", loader, null, LOG);

        assertTrue(result.fileChanged);
        assertEquals(1, result.addedKeys.size());
        assertEquals("shiny_new_key", result.addedKeys.get(0));

        String merged = new String(Files.readAllBytes(userFile), StandardCharsets.UTF_8);
        assertTrue(merged.contains("shiny_new_key: true"), "new key appended");
        assertTrue(merged.contains("Newly added in this release"), "comment preserved");
        assertTrue(merged.contains("old_key: 99"), "user value preserved");
        loader.close();
    }

    @Test
    void noChangeWhenAllKeysPresent(@TempDir Path tmp) throws IOException {
        Path resourcesDir = tmp.resolve("resources");
        Files.createDirectories(resourcesDir);
        Files.write(resourcesDir.resolve("config.yml"),
                "channel: CHANNEL\n".getBytes(StandardCharsets.UTF_8));

        URLClassLoader loader = new URLClassLoader(new URL[] { resourcesDir.toUri().toURL() });

        Path userFile = tmp.resolve("config.yml");
        Files.write(userFile, "channel: 1\n".getBytes(StandardCharsets.UTF_8));

        ConfigAutoMerger.MergeResult result = ConfigAutoMerger.mergeMissing(
                userFile.toFile(), "config.yml", loader, null, LOG);

        assertEquals(0, result.addedKeys.size());
        loader.close();
    }
}
