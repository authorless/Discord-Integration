package di.dilogin.controller.dbconnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import di.dilogin.controller.MainController;
import di.internal.controller.file.ConfigManager;

/**
 * MySQL controller backed by HikariCP connection pool.
 */
public class DBConnectionMysqlImpl implements DBConnection {

    private static HikariDataSource dataSource;

    public DBConnectionMysqlImpl() {
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
            MainController.getDIApi().getInternalController().getLogger()
                    .log(Level.SEVERE, "DBConnectionMysqlImpl - getConnection", e);
            throw new IllegalStateException("Failed to obtain MySQL connection", e);
        }
    }

    private void initPool() {
        MainController.getDIApi().getInternalController().getLogger().info("Database connection type: MYSQL (Hikari pool)");
        ConfigManager cm = MainController.getDIApi().getInternalController().getConfigManager();
        String host = cm.getString("database_host");
        String port = cm.getString("database_port");
        String user = cm.getString("database_username");
        String password = cm.getString("database_password");
        String table = cm.getString("database_table");
        String autoReconnect = cm.getString("database_autoReconnect");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + table
                + "?characterEncoding=utf8&autoReconnect=" + autoReconnect;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(30 * 60_000);
        config.setPoolName("DILogin-MySQL");

        try {
            dataSource = new HikariDataSource(config);
            initTables();
        } catch (Exception e) {
            MainController.getDIApi().getInternalController().getLogger()
                    .log(Level.SEVERE, "DBConnectionMysqlImpl - initPool", e);
            MainController.getDIApi().getInternalController().disablePlugin();
        }
    }

    private static ArrayList<String> createTables() {
        ArrayList<String> sql = new ArrayList<>();
        sql.add("CREATE TABLE IF NOT EXISTS user(username varchar(17) primary key, discord_id varchar(30));");
        return sql;
    }

    private void initTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String s : createTables())
                stmt.execute(s);
        } catch (SQLException e) {
            MainController.getDIApi().getInternalController().getLogger()
                    .log(Level.SEVERE, "DBConnectionMysqlImpl - initTables", e);
        }
    }
}
