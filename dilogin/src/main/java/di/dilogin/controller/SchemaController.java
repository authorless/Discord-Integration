package di.dilogin.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Tracks the schema version of the shared DI database. When several Bukkit
 * servers point at the same MySQL instance (cross-server identity setup) this
 * guards against silent data corruption caused by mismatched plugin versions.
 *
 * Behaviour:
 * <ul>
 *   <li>First boot: the {@code di_schema} table is created and seeded with
 *       {@link #CURRENT_SCHEMA_VERSION}.</li>
 *   <li>Stored version equal to current: OK, no log.</li>
 *   <li>Stored version <em>higher</em> than current: another server already
 *       upgraded the schema; this older plugin refuses to start to avoid
 *       writing in an outdated layout.</li>
 *   <li>Stored version <em>lower</em>: this plugin updates the row and logs a
 *       warning so the operator knows the other servers must be bumped.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SchemaController {

    /** Bump whenever the {@code user} table or any other shared table changes. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final String CREATE_SQL =
            "CREATE TABLE IF NOT EXISTS di_schema (" +
                    "id INT PRIMARY KEY," +
                    "version INT NOT NULL," +
                    "plugin_version VARCHAR(32)," +
                    "updated_at BIGINT NOT NULL" +
                    ");";

    public enum Result {
        OK,
        UPGRADED_FROM_OLDER,
        REJECTED_NEWER_DB
    }

    /**
     * Validates and reconciles the schema version. Pure DB call, runs on the
     * caller's thread; cheap.
     */
    public static Result check(String pluginVersion, Logger logger) {
        try (Connection conn = DBController.getConnect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_SQL);

            int stored = readVersion(conn);
            if (stored == 0) {
                writeVersion(conn, CURRENT_SCHEMA_VERSION, pluginVersion);
                logger.info("Initialised DI schema at version " + CURRENT_SCHEMA_VERSION + ".");
                return Result.OK;
            }

            if (stored == CURRENT_SCHEMA_VERSION) {
                return Result.OK;
            }

            if (stored > CURRENT_SCHEMA_VERSION) {
                logger.severe("Database schema is at version " + stored
                        + " but this plugin only supports " + CURRENT_SCHEMA_VERSION + ".");
                logger.severe("Another server in your network is running a newer DILogin. "
                        + "Update this server to match or point it at a separate database.");
                return Result.REJECTED_NEWER_DB;
            }

            // stored < CURRENT_SCHEMA_VERSION
            writeVersion(conn, CURRENT_SCHEMA_VERSION, pluginVersion);
            logger.warning("Upgraded DI schema from version " + stored + " to " + CURRENT_SCHEMA_VERSION + ".");
            logger.warning("Other servers using this database must be updated to the same plugin version "
                    + "to avoid data corruption.");
            return Result.UPGRADED_FROM_OLDER;
        } catch (SQLException e) {
            logger.warning("Schema check failed (" + e.getMessage() + "); proceeding without cross-server guard.");
            return Result.OK;
        }
    }

    private static int readVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT version FROM di_schema WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next())
                return rs.getInt(1);
            return 0;
        }
    }

    private static void writeVersion(Connection conn, int version, String pluginVersion) throws SQLException {
        // Portable upsert: try update first, insert if no rows changed.
        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE di_schema SET version = ?, plugin_version = ?, updated_at = ? WHERE id = 1")) {
            upd.setInt(1, version);
            upd.setString(2, pluginVersion);
            upd.setLong(3, System.currentTimeMillis());
            int rows = upd.executeUpdate();
            if (rows > 0)
                return;
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO di_schema (id, version, plugin_version, updated_at) VALUES (1, ?, ?, ?)")) {
            ins.setInt(1, version);
            ins.setString(2, pluginVersion);
            ins.setLong(3, System.currentTimeMillis());
            ins.executeUpdate();
        }
    }
}
