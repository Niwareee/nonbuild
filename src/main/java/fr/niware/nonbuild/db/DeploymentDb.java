package fr.niware.nonbuild.db;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface DeploymentDb {

    void initialize();

    void close();

    Map<String, DeployedInstanceRow> loadAll(List<String> order) throws SQLException;

    DeployedInstanceRow loadByName(String instanceName) throws SQLException;

    List<DeployedInstanceRow> loadByArena(String arena) throws SQLException;

    void save(DeployedInstanceRow instance) throws SQLException;

    void delete(String instanceName) throws SQLException;

    void clear() throws SQLException;

    void renameArena(String instanceName, String newArena) throws SQLException;

    int count() throws SQLException;
}