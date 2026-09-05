package fr.niware.nonbuild.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import fr.niware.nonbuild.Settings;

public class SqlSystemConfigDb implements SystemConfigDb {

    private static final String TABLE = "system_configs";

    private static final String SQL_SELECT = "SELECT config_value FROM " + TABLE + " WHERE config_key = ?";
    private static final String SQL_SAVE = """
            INSERT INTO %s (config_key, config_value, updated_at) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = VALUES(updated_at)
            """.formatted(TABLE);

    private final HikariDataSource dataSource;

    public SqlSystemConfigDb(Settings settings) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(String.format("jdbc:mariadb://%s:%d/%s",
                settings.dbHost(), settings.dbPort(), settings.dbName()));
        config.setUsername(settings.dbUser());
        config.setPassword(settings.dbPassword());
        config.setMaximumPoolSize(settings.dbPoolSize());
        config.setMinimumIdle(1);
        config.setIdleTimeout(600000);
        config.setConnectionTimeout(10000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "256");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public byte[] load(String key) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("config_value");
                }
            }
        }
        return null;
    }

    @Override
    public void save(String key, byte[] value) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SAVE)) {
            ps.setString(1, key);
            ps.setBytes(2, value);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
