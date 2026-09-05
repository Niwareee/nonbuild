package fr.niware.nonbuild.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import fr.niware.nonbuild.Settings;

public class SqlArenaDefinitionDb implements ArenaDefinitionDb {

    private static final String TABLE = "arena_definitions";

    /** Columns for metadata queries (excludes the heavy `schematic` BLOB). */
    private static final String META_COLS = "slug, display_name, world, game_mode, "
            + "corner1_x, corner1_y, corner1_z, corner2_x, corner2_y, corner2_z, "
            + "center_x, center_y, center_z, center_yaw, center_pitch, "
            + "spawn1_x, spawn1_y, spawn1_z, spawn1_yaw, spawn1_pitch, "
            + "spawn2_x, spawn2_y, spawn2_z, spawn2_yaw, spawn2_pitch, saved_at";

    private static final String SQL_SELECT_ALL = "SELECT " + META_COLS + " FROM " + TABLE + " ORDER BY slug";
    private static final String SQL_SELECT_BY_SLUG = "SELECT " + META_COLS + " FROM " + TABLE + " WHERE slug = ?";
    private static final String SQL_SELECT_BY_GAME_MODE = "SELECT " + META_COLS + " FROM " + TABLE + " WHERE game_mode = ? ORDER BY slug";
    private static final String SQL_SELECT_SCHEMATIC = "SELECT schematic FROM " + TABLE + " WHERE slug = ?";
    private static final String SQL_COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private static final String SQL_SAVE = """
            INSERT INTO %s (
                slug, display_name, world, game_mode,
                corner1_x, corner1_y, corner1_z,
                corner2_x, corner2_y, corner2_z,
                center_x, center_y, center_z, center_yaw, center_pitch,
                spawn1_x, spawn1_y, spawn1_z, spawn1_yaw, spawn1_pitch,
                spawn2_x, spawn2_y, spawn2_z, spawn2_yaw, spawn2_pitch,
                saved_at, schematic
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            ) ON DUPLICATE KEY UPDATE
                display_name=VALUES(display_name), world=VALUES(world), game_mode=VALUES(game_mode),
                corner1_x=VALUES(corner1_x), corner1_y=VALUES(corner1_y), corner1_z=VALUES(corner1_z),
                corner2_x=VALUES(corner2_x), corner2_y=VALUES(corner2_y), corner2_z=VALUES(corner2_z),
                center_x=VALUES(center_x), center_y=VALUES(center_y), center_z=VALUES(center_z),
                center_yaw=VALUES(center_yaw), center_pitch=VALUES(center_pitch),
                spawn1_x=VALUES(spawn1_x), spawn1_y=VALUES(spawn1_y), spawn1_z=VALUES(spawn1_z),
                spawn1_yaw=VALUES(spawn1_yaw), spawn1_pitch=VALUES(spawn1_pitch),
                spawn2_x=VALUES(spawn2_x), spawn2_y=VALUES(spawn2_y), spawn2_z=VALUES(spawn2_z),
                spawn2_yaw=VALUES(spawn2_yaw), spawn2_pitch=VALUES(spawn2_pitch),
                saved_at=VALUES(saved_at), schematic=VALUES(schematic)
            """.formatted(TABLE);

    private static final String SQL_SAVE_SCHEMATIC = "UPDATE " + TABLE + " SET schematic = ? WHERE slug = ?";

    private static final String SQL_DELETE = "DELETE FROM " + TABLE + " WHERE slug = ?";

    private final Logger log;
    private final HikariDataSource dataSource;

    public SqlArenaDefinitionDb(Settings settings, Logger log) {
        this.log = log;
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
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public List<ArenaDefinitionRow> loadAll() throws SQLException {
        List<ArenaDefinitionRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(toMetaRow(rs));
            }
        }
        return rows;
    }

    @Override
    public ArenaDefinitionRow loadBySlug(String slug) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_SLUG)) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return toMetaRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<ArenaDefinitionRow> loadByGameMode(String gameMode) throws SQLException {
        List<ArenaDefinitionRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_GAME_MODE)) {
            ps.setString(1, gameMode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(toMetaRow(rs));
                }
            }
        }
        return rows;
    }

    @Override
    public byte[] loadSchematic(String slug) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_SCHEMATIC)) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("schematic");
                }
            }
        }
        return null;
    }

    @Override
    public void save(ArenaDefinitionRow row) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SAVE)) {
            int i = 1;
            ps.setString(i++, row.slug());
            ps.setString(i++, row.displayName());
            ps.setString(i++, row.world());
            ps.setString(i++, row.gameMode());
            ps.setInt(i++, row.corner1X());
            ps.setInt(i++, row.corner1Y());
            ps.setInt(i++, row.corner1Z());
            ps.setInt(i++, row.corner2X());
            ps.setInt(i++, row.corner2Y());
            ps.setInt(i++, row.corner2Z());
            ps.setDouble(i++, row.centerX());
            ps.setDouble(i++, row.centerY());
            ps.setDouble(i++, row.centerZ());
            ps.setFloat(i++, row.centerYaw());
            ps.setFloat(i++, row.centerPitch());
            ps.setDouble(i++, row.spawn1X());
            ps.setDouble(i++, row.spawn1Y());
            ps.setDouble(i++, row.spawn1Z());
            ps.setFloat(i++, row.spawn1Yaw());
            ps.setFloat(i++, row.spawn1Pitch());
            ps.setDouble(i++, row.spawn2X());
            ps.setDouble(i++, row.spawn2Y());
            ps.setDouble(i++, row.spawn2Z());
            ps.setFloat(i++, row.spawn2Yaw());
            ps.setFloat(i++, row.spawn2Pitch());
            ps.setLong(i++, row.savedAt());
            if (row.schematic() != null) {
                ps.setBytes(i, row.schematic());
            } else {
                ps.setNull(i, java.sql.Types.LONGVARBINARY);
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void saveSchematic(String slug, byte[] data) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SAVE_SCHEMATIC)) {
            ps.setBytes(1, data);
            ps.setString(2, slug);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(String slug) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setString(1, slug);
            ps.executeUpdate();
        }
    }

    @Override
    public int count() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_COUNT);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Build a metadata-only row from a ResultSet that does NOT include the schematic column.
     */
    private ArenaDefinitionRow toMetaRow(ResultSet rs) throws SQLException {
        return new ArenaDefinitionRow(
                rs.getString("slug"),
                rs.getString("display_name"),
                rs.getString("world"),
                rs.getString("game_mode"),
                rs.getInt("corner1_x"), rs.getInt("corner1_y"), rs.getInt("corner1_z"),
                rs.getInt("corner2_x"), rs.getInt("corner2_y"), rs.getInt("corner2_z"),
                rs.getDouble("center_x"), rs.getDouble("center_y"), rs.getDouble("center_z"),
                rs.getFloat("center_yaw"), rs.getFloat("center_pitch"),
                rs.getDouble("spawn1_x"), rs.getDouble("spawn1_y"), rs.getDouble("spawn1_z"),
                rs.getFloat("spawn1_yaw"), rs.getFloat("spawn1_pitch"),
                rs.getDouble("spawn2_x"), rs.getDouble("spawn2_y"), rs.getDouble("spawn2_z"),
                rs.getFloat("spawn2_yaw"), rs.getFloat("spawn2_pitch"),
                rs.getLong("saved_at"),
                null
        );
    }
}
