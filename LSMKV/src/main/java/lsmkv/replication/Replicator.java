// Replicator.java
package src.main.java.lsmkv.replication;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

public class Replicator {
    private final Path dataDir;
    private final ConcurrentHashMap<String, byte[]> memtable = new ConcurrentHashMap<>();

    // ✅ Take dataDir as a parameter
    public Replicator(Path dataDir) {
        this.dataDir = dataDir;
    }

    // Single put
    public void put(String key, byte[] value) {
        memtable.put(key, value);
        // TODO: flush to SSTable when full
    }

    // Single get
    public byte[] get(String key) {
        return memtable.getOrDefault(key, null);
    }

    //  Batch put
    public void putBatch(Map<String, byte[]> entries) {
        memtable.putAll(entries);
        // TODO: batching flush strategy can be applied here
    }

    // Batch get
    public Map<String, byte[]> getBatch(List<String> keys) {
        Map<String, byte[]> result = new HashMap<>();
        for (String key : keys) {
            if (memtable.containsKey(key)) {
                result.put(key, memtable.get(key));
            }
        }
        return result;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Map<String, byte[]> getRange(String startKey, String endKey) {
        Map<String, byte[]> result = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : memtable.entrySet()) {
            String k = entry.getKey();
            if (k.compareTo(startKey) >= 0 && k.compareTo(endKey) <= 0) {
                result.put(k, entry.getValue());
            }
        }
        return result;
    }

}
