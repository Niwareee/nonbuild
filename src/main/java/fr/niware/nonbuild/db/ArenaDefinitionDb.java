package fr.niware.nonbuild.db;

import java.sql.SQLException;
import java.util.List;

public interface ArenaDefinitionDb {

    void close();

    /**
     * Load all arena metadata (no schematic data).
     */
    List<ArenaDefinitionRow> loadAll() throws SQLException;

    /**
     * Load arena metadata by slug (no schematic data).
     */
    ArenaDefinitionRow loadBySlug(String slug) throws SQLException;

    /**
     * Load arena metadata by game mode (no schematic data).
     */
    List<ArenaDefinitionRow> loadByGameMode(String gameMode) throws SQLException;

    /**
     * Load only the schematic bytes for a given slug.
     * Returns null if the slug doesn't exist or has no schematic.
     */
    byte[] loadSchematic(String slug) throws SQLException;

    /**
     * Save arena metadata + schematic.
     */
    void save(ArenaDefinitionRow definition) throws SQLException;

    /**
     * Save only the schematic bytes for an existing slug.
     */
    void saveSchematic(String slug, byte[] data) throws SQLException;

    /**
     * Delete arena definition (metadata + schematic).
     */
    void delete(String slug) throws SQLException;

    /**
     * Count arena definitions.
     */
    int count() throws SQLException;
}
