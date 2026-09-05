package fr.niware.nonbuild.db;

import java.sql.SQLException;

/**
 * Generic key/value blob storage for plugin-wide system data
 * (spawn schematic, future configs, etc.).
 */
public interface SystemConfigDb {

    /**
     * Load binary data for the given key.
     * Returns null if the key does not exist.
     */
    byte[] load(String key) throws SQLException;

    /**
     * Save binary data for the given key (upsert).
     */
    void save(String key, byte[] value) throws SQLException;

    /**
     * Close the underlying connection pool.
     */
    void close();
}
