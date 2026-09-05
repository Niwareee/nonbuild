package fr.niware.nonbuild.db;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


import org.bukkit.Bukkit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import fr.niware.nonbuild.Settings;
import fr.niware.nonbuild.Settings;

public class SqlDeploymentDb implements DeploymentDb {

    private static final String TABLE = "arenas";

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS %s (
                instance_name VARCHAR(255) PRIMARY KEY,
                arena VARCHAR(255) NOT NULL,
                world VARCHAR(255) NOT NULL,
                center_x DOUBLE NOT NULL,
                center_y DOUBLE NOT NULL,
                center_z DOUBLE NOT NULL,
                center_yaw FLOAT NOT NULL,
                center_pitch FLOAT NOT NULL,
                corner1_x INT,
                corner1_y INT,
                corner1_z INT,
                corner2_x INT,
                corner2_y INT,
                corner2_z INT,
                spawn1_x DOUBLE,
                spawn1_y DOUBLE,
                spawn1_z DOUBLE,
                spawn1_yaw FLOAT,
                spawn1_pitch FLOAT,
                spawn2_x DOUBLE,
                spawn2_y DOUBLE,
                spawn2_z DOUBLE,
                spawn2_yaw FLOAT,
                spawn2_pitch FLOAT,
                cell_min_x INT,
                cell_min_z INT,
                cell_max_x INT,
                cell_max_z INT,
                deployed_at BIGINT NOT NULL,
                INDEX idx_arena (arena)
            )
            """.formatted(TABLE);

    private static final String INSERT = """
            INSERT INTO %s (
                instance_name, arena, world,
                center_x, center_y, center_z, center_yaw, center_pitch,
                corner1_x, corner1_y, corner1_z,
                corner2_x, corner2_y, corner2_z,
                spawn1_x, spawn1_y, spawn1_z, spawn1_yaw, spawn1_pitch,
                spawn2_x, spawn2_y, spawn2_z, spawn2_yaw, spawn2_pitch,
                cell_min_x, cell_min_z, cell_max_x, cell_max_z,
                deployed_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            ) ON DUPLICATE KEY UPDATE
                arena=VALUES(arena), world=VALUES(world),
                center_x=VALUES(center_x), center_y=VALUES(center_y), center_z=VALUES(center_z),
                center_yaw=VALUES(center_yaw), center_pitch=VALUES(center_pitch),
                corner1_x=VALUES(corner1_x), corner1_y=VALUES(corner1_y), corner1_z=VALUES(corner1_z),
                corner2_x=VALUES(corner2_x), corner2_y=VALUES(corner2_y), corner2_z=VALUES(corner2_z),
                spawn1_x=VALUES(spawn1_x), spawn1_y=VALUES(spawn1_y), spawn1_z=VALUES(spawn1_z),
                spawn1_yaw=VALUES(spawn1_yaw), spawn1_pitch=VALUES(spawn1_pitch),
                spawn2_x=VALUES(spawn2_x), spawn2_y=VALUES(spawn2_y), spawn2_z=VALUES(spawn2_z),
                spawn2_yaw=VALUES(spawn2_yaw), spawn2_pitch=VALUES(spawn2_pitch),
                cell_min_x=VALUES(cell_min_x), cell_min_z=VALUES(cell_min_z),
                cell_max_x=VALUES(cell_max_x), cell_max_z=VALUES(cell_max_z),
                deployed_at=VALUES(deployed_at)
            """.formatted(TABLE);

    private static final String SELECT_ALL = "SELECT * FROM " + TABLE + " ORDER BY deployed_at DESC";
    private static final String SELECT_BY_NAME = "SELECT * FROM " + TABLE + " WHERE instance_name = ?";
    private static final String SELECT_BY_ARENA = "SELECT * FROM " + TABLE + " WHERE arena = ?";
    private static final String DELETE_BY_NAME = "DELETE FROM " + TABLE + " WHERE instance_name = ?";
    private static final String DELETE_ALL = "DELETE FROM " + TABLE;
    private static final String UPDATE_ARENA = "UPDATE " + TABLE + " SET arena = ? WHERE instance_name = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private final HikariDataSource dataSource;

    public SqlDeploymentDb(Settings settings) {
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
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public void initialize() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CREATE_TABLE)) {
            ps.execute();
            Bukkit.getLogger().info("[NonBuild] Base de données initialisée : " + TABLE);
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[NonBuild] Erreur d'initialisation de la base : " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public Map<String, DeployedInstanceRow> loadAll(List<String> order) throws SQLException {
        Map<String, DeployedInstanceRow> map = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DeployedInstanceRow row = mapRow(rs);
                    map.put(row.instanceName(), row);
                }
            }
        }
        return map;
    }

    @Override
    public DeployedInstanceRow loadByName(String instanceName) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_NAME)) {
            ps.setString(1, instanceName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        }
    }

    @Override
    public List<DeployedInstanceRow> loadByArena(String arena) throws SQLException {
        List<DeployedInstanceRow> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ARENA)) {
            ps.setString(1, arena);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public void save(DeployedInstanceRow instance) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT)) {
            setInstanceParams(ps, instance);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(String instanceName) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_NAME)) {
            ps.setString(1, instanceName);
            ps.executeUpdate();
        }
    }

    @Override
    public void clear() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_ALL)) {
            ps.executeUpdate();
        }
    }

    @Override
    public void renameArena(String instanceName, String newArena) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_ARENA)) {
            ps.setString(1, newArena);
            ps.setString(2, instanceName);
            ps.executeUpdate();
        }
    }

    @Override
    public int count() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    private static void setInstanceParams(PreparedStatement ps, DeployedInstanceRow inst) throws SQLException {
        int i = 1;
        ps.setString(i++, inst.instanceName());
        ps.setString(i++, inst.arena());
        ps.setString(i++, inst.world());

        ps.setDouble(i++, inst.centerX());
        ps.setDouble(i++, inst.centerY());
        ps.setDouble(i++, inst.centerZ());
        ps.setFloat(i++, inst.centerYaw());
        ps.setFloat(i++, inst.centerPitch());

        ps.setObject(i++, inst.corner1X() != null ? inst.corner1X() : null);
        ps.setObject(i++, inst.corner1Y() != null ? inst.corner1Y() : null);
        ps.setObject(i++, inst.corner1Z() != null ? inst.corner1Z() : null);

        ps.setObject(i++, inst.corner2X() != null ? inst.corner2X() : null);
        ps.setObject(i++, inst.corner2Y() != null ? inst.corner2Y() : null);
        ps.setObject(i++, inst.corner2Z() != null ? inst.corner2Z() : null);

        ps.setObject(i++, inst.spawn1X() != null ? inst.spawn1X() : null);
        ps.setObject(i++, inst.spawn1Y() != null ? inst.spawn1Y() : null);
        ps.setObject(i++, inst.spawn1Z() != null ? inst.spawn1Z() : null);
        ps.setObject(i++, inst.spawn1Yaw() != null ? inst.spawn1Yaw() : null);
        ps.setObject(i++, inst.spawn1Pitch() != null ? inst.spawn1Pitch() : null);

        ps.setObject(i++, inst.spawn2X() != null ? inst.spawn2X() : null);
        ps.setObject(i++, inst.spawn2Y() != null ? inst.spawn2Y() : null);
        ps.setObject(i++, inst.spawn2Z() != null ? inst.spawn2Z() : null);
        ps.setObject(i++, inst.spawn2Yaw() != null ? inst.spawn2Yaw() : null);
        ps.setObject(i++, inst.spawn2Pitch() != null ? inst.spawn2Pitch() : null);

        ps.setObject(i++, inst.cellMinX() != null ? inst.cellMinX() : null);
        ps.setObject(i++, inst.cellMinZ() != null ? inst.cellMinZ() : null);
        ps.setObject(i++, inst.cellMaxX() != null ? inst.cellMaxX() : null);
        ps.setObject(i++, inst.cellMaxZ() != null ? inst.cellMaxZ() : null);

        ps.setLong(i++, inst.deployedAt());
    }

    private static DeployedInstanceRow mapRow(ResultSet rs) throws SQLException {
        return new DeployedInstanceRow(
                rs.getString("instance_name"),
                rs.getString("arena"),
                rs.getString("world"),
                rs.getDouble("center_x"),
                rs.getDouble("center_y"),
                rs.getDouble("center_z"),
                rs.getFloat("center_yaw"),
                rs.getFloat("center_pitch"),
                rs.wasNull() ? null : rs.getInt("corner1_x"),
                rs.wasNull() ? null : rs.getInt("corner1_y"),
                rs.wasNull() ? null : rs.getInt("corner1_z"),
                rs.wasNull() ? null : rs.getInt("corner2_x"),
                rs.wasNull() ? null : rs.getInt("corner2_y"),
                rs.wasNull() ? null : rs.getInt("corner2_z"),
                rs.wasNull() ? null : rs.getDouble("spawn1_x"),
                rs.wasNull() ? null : rs.getDouble("spawn1_y"),
                rs.wasNull() ? null : rs.getDouble("spawn1_z"),
                rs.wasNull() ? null : rs.getFloat("spawn1_yaw"),
                rs.wasNull() ? null : rs.getFloat("spawn1_pitch"),
                rs.wasNull() ? null : rs.getDouble("spawn2_x"),
                rs.wasNull() ? null : rs.getDouble("spawn2_y"),
                rs.wasNull() ? null : rs.getDouble("spawn2_z"),
                rs.wasNull() ? null : rs.getFloat("spawn2_yaw"),
                rs.wasNull() ? null : rs.getFloat("spawn2_pitch"),
                rs.wasNull() ? null : rs.getInt("cell_min_x"),
                rs.wasNull() ? null : rs.getInt("cell_min_z"),
                rs.wasNull() ? null : rs.getInt("cell_max_x"),
                rs.wasNull() ? null : rs.getInt("cell_max_z"),
                rs.getLong("deployed_at")
        );
    }
}
