package di.internal.controller.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

/**
 * Brings a user's YAML config up to date by appending any top-level keys
 * present in the bundled defaults but missing from disk.
 *
 * Preserves the user's existing values, ordering and comments. New keys are
 * appended at the end together with their original comment block from the
 * bundled file, under a clearly marked banner so the user can review them.
 */
public final class ConfigAutoMerger {

    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*:.*$");

    private ConfigAutoMerger() {}

    public static class MergeResult {
        public final List<String> addedKeys;
        public final boolean fileChanged;

        MergeResult(List<String> addedKeys, boolean fileChanged) {
            this.addedKeys = addedKeys;
            this.fileChanged = fileChanged;
        }
    }

    /**
     * Append missing top-level keys from the bundled resource into the user's
     * file on disk, then reload the in-memory map.
     */
    public static MergeResult mergeMissing(File userFile, String resourceName, ClassLoader classLoader,
                                           ConfigManager configManager, Logger logger) {
        List<String> added = new ArrayList<>();

        if (userFile == null || !userFile.exists()) {
            return new MergeResult(added, false);
        }

        List<String> bundledLines = readResourceLines(resourceName, classLoader, logger);
        if (bundledLines.isEmpty()) {
            return new MergeResult(added, false);
        }

        Map<String, int[]> bundledKeyRanges = mapTopLevelKeys(bundledLines);
        if (bundledKeyRanges.isEmpty()) {
            return new MergeResult(added, false);
        }

        List<String> userLines = readFileLines(userFile, logger);
        Map<String, int[]> userKeyRanges = mapTopLevelKeys(userLines);

        StringBuilder appendix = new StringBuilder();
        for (Map.Entry<String, int[]> entry : bundledKeyRanges.entrySet()) {
            String key = entry.getKey();
            if (userKeyRanges.containsKey(key))
                continue;
            int[] range = entry.getValue();
            // include comment block immediately above the key
            int blockStart = backtrackToCommentBlockStart(bundledLines, range[0]);
            for (int i = blockStart; i <= range[1]; i++) {
                appendix.append(bundledLines.get(i)).append(System.lineSeparator());
            }
            added.add(key);
        }

        if (added.isEmpty()) {
            return new MergeResult(added, false);
        }

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(userFile.toPath(),
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND))) {
            writer.println();
            writer.println("# ----- Auto-merged on " + new java.util.Date() + " -----");
            writer.println("# The following keys were missing from your config and were added with bundled defaults.");
            writer.println("# Review and adjust the values to match your setup.");
            writer.println();
            writer.print(appendix);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "ConfigAutoMerger - failed to append missing keys", e);
            return new MergeResult(added, false);
        }

        reloadConfigManager(userFile, configManager, logger);

        logger.info("Auto-merged " + added.size() + " missing config key(s): " + added);
        return new MergeResult(added, true);
    }

    private static List<String> readResourceLines(String resourceName, ClassLoader classLoader, Logger logger) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = classLoader.getResourceAsStream(resourceName)) {
            if (in == null)
                return lines;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null)
                    lines.add(line);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "ConfigAutoMerger - failed to read bundled resource " + resourceName, e);
        }
        return lines;
    }

    private static List<String> readFileLines(File file, Logger logger) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null)
                lines.add(line);
        } catch (IOException e) {
            logger.log(Level.WARNING, "ConfigAutoMerger - failed to read user file " + file, e);
        }
        return lines;
    }

    /**
     * Walk the file once and record [startLine, endLine] for every top-level key,
     * preserving definition order.
     */
    private static Map<String, int[]> mapTopLevelKeys(List<String> lines) {
        Map<String, int[]> ranges = new LinkedHashMap<>();
        String currentKey = null;
        int currentStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = TOP_LEVEL_KEY.matcher(line);
            if (m.matches()) {
                if (currentKey != null) {
                    ranges.put(currentKey, new int[] { currentStart, i - 1 });
                }
                currentKey = m.group(1);
                currentStart = i;
            }
        }
        if (currentKey != null) {
            ranges.put(currentKey, new int[] { currentStart, lines.size() - 1 });
        }
        return ranges;
    }

    /**
     * Walk backwards from {@code keyLineIndex} including any contiguous comment
     * lines (and optional blank lines between them) to capture the docstring
     * that originally sat above the key in the bundled file.
     */
    private static int backtrackToCommentBlockStart(List<String> lines, int keyLineIndex) {
        int idx = keyLineIndex - 1;
        int firstCommentLine = keyLineIndex;
        while (idx >= 0) {
            String trimmed = lines.get(idx).trim();
            if (trimmed.startsWith("#")) {
                firstCommentLine = idx;
                idx--;
            } else if (trimmed.isEmpty() && firstCommentLine != keyLineIndex) {
                // blank line between two comment runs — keep walking
                idx--;
            } else {
                break;
            }
        }
        return firstCommentLine;
    }

    @SuppressWarnings("unchecked")
    private static void reloadConfigManager(File userFile, ConfigManager configManager, Logger logger) {
        if (configManager == null)
            return;
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(userFile)) {
            Object loaded = yaml.load(fis);
            if (loaded instanceof Map) {
                configManager.setData((Map<String, Object>) loaded);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "ConfigAutoMerger - failed to reload config after merge", e);
        }
    }
}
