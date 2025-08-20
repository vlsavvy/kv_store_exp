// TODO: Implement StorageEngine.java
package lsmkv.engine;

import lsmkv.config.Config;
import lsmkv.backpressure.WriteQueue;
import lsmkv.replication.Replicator;

import java.io.IOException;
import java.nio.file.Path;

public class StorageEngine implements AutoCloseable {
    private final Config cfg;
    private final Path dataDir;
    private final MemTable mem;
    private final WAL wal;
    private final SSTable sst;
    private final Compactor compactor;
    private final WriteQueue writeQueue;
    private final Replicator replicator;

    public StorageEngine(Path dataDir, Config cfg) throws IOException {
        this.cfg = cfg;
        this.dataDir = dataDir;
        this.mem = new MemTable();
        this.wal = new WAL(dataDir.resolve("wal.log"), cfg);
        this.sst = new SSTable(dataDir, cfg);
        this.compactor = new Compactor(sst, cfg);
        this.writeQueue = new WriteQueue(cfg.writeQueueCapacity);
        this.replicator = null; // can be set later
    }

    public void start() throws IOException {
        wal.open();
        mem.recoverFromWAL(wal);
        compactor.start();
    }

    public void put(String key, byte[] value) throws IOException {
        // enqueue for backpressure
        writeQueue.enqueue(() -> {
            wal.appendPut(key, value);
            mem.put(key, value);
            compactor.maybeSchedule();
        });
        // replicate if needed
        if (replicator != null) replicator.replicatePut(key, value);
    }

    public byte[] get(String key) {
        byte[] val = mem.get(key);
        if (val != null) return val;
        return sst.get(key);
    }

    public void delete(String key) throws IOException {
        writeQueue.enqueue(() -> {
            wal.appendDel(key);
            mem.delete(key);
            compactor.maybeSchedule();
        });
        if (replicator != null) replicator.replicateDel(key);
    }

    @Override
    public void close() throws IOException {
        wal.close();
        compactor.stop();
        writeQueue.shutdown();
    }
}
