package fr.niware.nonbuild.db;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryDeploymentDb implements DeploymentDb {

    private final Map<String, DeployedInstanceRow> data = new LinkedHashMap<>();

    @Override
    public void initialize() {
    }

    @Override
    public void close() {
    }

    @Override
    public Map<String, DeployedInstanceRow> loadAll(List<String> order) throws SQLException {
        Map<String, DeployedInstanceRow> result = new LinkedHashMap<>();
        for (String name : order) {
            if (data.containsKey(name)) {
                result.put(name, data.get(name));
            }
        }
        for (Map.Entry<String, DeployedInstanceRow> entry : data.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    @Override
    public DeployedInstanceRow loadByName(String instanceName) throws SQLException {
        return data.get(instanceName);
    }

    @Override
    public List<DeployedInstanceRow> loadByArena(String arena) throws SQLException {
        List<DeployedInstanceRow> list = new ArrayList<>();
        for (DeployedInstanceRow row : data.values()) {
            if (row.arena().equals(arena)) {
                list.add(row);
            }
        }
        return list;
    }

    @Override
    public void save(DeployedInstanceRow instance) throws SQLException {
        data.put(instance.instanceName(), instance);
    }

    @Override
    public void delete(String instanceName) throws SQLException {
        data.remove(instanceName);
    }

    @Override
    public void clear() throws SQLException {
        data.clear();
    }

    @Override
    public void renameArena(String instanceName, String newArena) throws SQLException {
        DeployedInstanceRow row = data.get(instanceName);
        if (row != null) {
        }
    }

    @Override
    public int count() throws SQLException {
        return data.size();
    }
}
