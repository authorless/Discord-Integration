package di.dilogin.minecraft.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import di.dilogin.controller.MainController;
import di.dilogin.entity.UserSession;
import di.dilogin.minecraft.controller.EncryptionController;

/**
 * This class provides methods to manage user sessions, including adding sessions,
 * removing sessions, and checking if a session exists.
 */
public class SessionFileController {

    /** The file where sessions will be stored. */
    private static final File SESSION_FILE = new File(MainController.getDIApi().getInternalController().getDataFolder(), "sessions.ser");

    /** Whitelist of classes allowed during deserialization to prevent gadget-chain attacks. */
    private static final Set<String> ALLOWED_CLASSES = new HashSet<>(Arrays.asList(
            "java.util.HashMap",
            "java.lang.Long",
            "java.lang.Number",
            "java.lang.String",
            "di.dilogin.entity.UserSession"
    ));

    /**
     * Hardened ObjectInputStream that rejects any class outside the whitelist.
     */
    private static final class WhitelistedObjectInputStream extends ObjectInputStream {
        WhitelistedObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            if (name.startsWith("[")) {
                return super.resolveClass(desc);
            }
            if (!ALLOWED_CLASSES.contains(name)) {
                throw new InvalidClassException("Unauthorized deserialization attempt: " + name);
            }
            return super.resolveClass(desc);
        }
    }

    /**
     * Adds a user session with the specified expiration time.
     *
     * @param userSession     The user session to add.
     * @param expirationTime The expiration time of the session.
     */
    public static void addSession(UserSession userSession, long expirationTime) {
    	HashMap<UserSession, Long> sessions = loadSessions();
        if (sessions == null) {
            sessions = new HashMap<>(); // Initialize sessions if it's null
        }
        userSession.setIp(EncryptionController.encrypt(userSession.getIp()));
        sessions.put(userSession, expirationTime);
        saveSessions(sessions);
    }

    /**
     * Removes a user session given the player name.
     *
     * @param playerName The name of the player whose session will be removed.
     */
    public static void removeSession(String playerName) {
        HashMap<UserSession, Long> sessions = loadSessions();
        sessions.entrySet().removeIf(entry -> Objects.equals(entry.getKey().getName(), playerName));
        saveSessions(sessions);
    }

    /**
     * Checks if a user session exists in the session cache.
     *
     * @param userSession The user session to check for existence.
     * @return true if the session exists, false otherwise.
     */
    public static boolean sessionExists(UserSession userSession) {
        HashMap<UserSession, Long> sessions = loadSessions();
        if (sessions == null) {
            return false;
        }
        return sessions.keySet().stream()
                .anyMatch(session -> sessionEquals(session, userSession));
    }

    /**
     * Saves the sessions to the session file.
     *
     * @param sessions The sessions to save.
     */
    private static void saveSessions(HashMap<UserSession, Long> sessions) {
        try {
            if (!SESSION_FILE.exists()) {
                SESSION_FILE.createNewFile();
            }
            
            try (FileOutputStream fileOut = new FileOutputStream(SESSION_FILE);
                 ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {
                objectOut.writeObject(sessions);
            } catch (IOException e) {
                MainController.getDIApi().getInternalController().getLogger().severe("Error saving sessions: " + e.getMessage());
            }
        } catch (IOException e) {
            MainController.getDIApi().getInternalController().getLogger().severe("Error creating file: " + e.getMessage());
        }
    }

    /**
     * Loads the sessions from the session file.
     *
     * @return The loaded sessions.
     */
    @SuppressWarnings("unchecked")
    private static HashMap<UserSession, Long> loadSessions() {
        if (!SESSION_FILE.exists()) {
            return null;
        }

        File dataFolder = MainController.getDIApi().getInternalController().getDataFolder();
        File sessionFile = new File(dataFolder, "sessions.ser");

        if (!sessionFile.exists()) {
            return new HashMap<>();
        }

        try (ObjectInputStream inputStream = new WhitelistedObjectInputStream(new FileInputStream(sessionFile))) {
            Object obj = inputStream.readObject();
            if (!(obj instanceof HashMap)) {
                MainController.getDIApi().getInternalController().getLogger()
                        .severe("Unexpected sessions.ser contents (not a HashMap), discarding.");
                return new HashMap<>();
            }
            return (HashMap<UserSession, Long>) obj;
        } catch (IOException | ClassNotFoundException e) {
            MainController.getDIApi().getInternalController().getLogger()
                    .severe("Error loading sessions: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Compares two user sessions for equality.
     *
     * @param session1 The first user session.
     * @param session2 The second user session.
     * @return true if the sessions are equal, false otherwise.
     */
    private static boolean sessionEquals(UserSession session1, UserSession session2) {
        return session1.getName().equals(session2.getName()) && session1.getIp().equals(session2.getIp());
    }
}
