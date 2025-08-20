// TODO: Implement MemTable.java
package lsmkv.engine;

import java.util.concurrent.ConcurrentSkipListMap;

public class MemTable {
    private final ConcurrentSkipListMap<String, byte[]> map = new ConcurrentSkipListMap<>();

    public void put(String key, byte[] value) {
        map.put(key, value);
    }

    public byte[] get(String key) {
        return map.get(key);
    }

    public void delete(String key) {
        map.remove(key);
    }

    public void recoverFromWAL(WAL wal) {
        // TODO: replay WAL
    }
}
