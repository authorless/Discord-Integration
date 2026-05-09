package di.dilogin.controller.dbconnection;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import di.dilogin.controller.MainController;
import di.internal.controller.InternalController;

/**
 * SQLite controller backed by HikariCP connection pool.
 * SQLite supports a single writer at a time, so the pool is constrained to 1.
 */
public class DBConnectionSqliteImpl implements DBConnection {

    private static HikariDataSource dataSource;

    private static final InternalController controller = MainController.getDIApi().getInternalController();

    public DBConnectionSqliteImpl() {
        // initialised lazily on first getConnect()
    }

    @Override
    public synchronized Connection getConnect() {
        if (dataSource == null || dataSource.isClosed()) {
            initPool();
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            controller.getLogger().log(Level.SEVERE, "DBConnectionSqliteImpl - getConnection", e);
            throw new IllegalStateException("Failed to obtain SQLite connection", e);
        }
    }

    private void initPool() {
        controller.getLogger().info("Database connection type: SQLITE (Hikari pool)");
        try {
            File dataFolder = new File(controller.getDataFolder().getAbsolutePath(), "users.db");
            if (!dataFolder.exists()) {
                if (!dataFolder.createNewFile()) {
                    controller.getLogger().severe("Failed to create database file");
                    controller.disablePlugin();
                    return;
                }
            }
            Class.forName("org.sqlite.JDBC");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dataFolder.getAbsolutePath());
            config.setMaximumPoolSize(1); // SQLite: single writer
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10_000);
            config.setPoolName("DILogin-SQLite");

            dataSource = new HikariDataSource(config);
            initTables();
        } catch (ClassNotFoundException | IOException e) {
            controller.getLogger().log(Level.SEVERE, "DBConnectionSqliteImpl - initPool", e);
            controller.disablePlugin();
        }
    }

    private static ArrayList<String> createTables() {
        ArrayList<String> sql = new ArrayList<>();
        sql.add("CREATE TABLE IF NOT EXISTS user(username text primary key, discord_id varchar(30));");
        return sql;
    }

    private void initTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String s : createTables())
                stmt.execute(s);
        } catch (SQLException e) {
            controller.getLogger().log(Level.SEVERE, "DBConnectionSqliteImpl - initTables", e);
        }
    }
}
