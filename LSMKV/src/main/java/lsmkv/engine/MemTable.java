package src.main.java.lsmkv.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class MemTable {
    private final Map<String, byte[]> map = new ConcurrentHashMap<>();

    public void put(String key, byte[] value) {
        map.put(key, value);
    }

    public byte[] get(String key) {
        return map.get(key);
    }

    public void delete(String key) {
        map.remove(key);
    }

    // Stub for WAL recovery for now
    public void recoverFromWAL(WAL wal) {
        // TODO: implement real recovery
    }
}
