// StorageEngine.java
package src.main.java.lsmkv.engine;


import src.main.java.lsmkv.backpressure.WriteQueue;
import src.main.java.lsmkv.config.Config;
import src.main.java.lsmkv.replication.Replicator;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class StorageEngine implements AutoCloseable, Closeable {
    private final Config cfg;
    private final Path dataDir;

    // Core components
    private final MemTable mem;
    private final WAL wal;
    private final SSTable sst;
    private final Compactor compactor;

    // Optional / pluggable modules
    private final WriteQueue writeQueue;
    private final Replicator replicator;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed  = new AtomicBoolean(false);

    public StorageEngine(Path dataDir, Config cfg, Replicator replicator) throws IOException {
        this.cfg = Objects.requireNonNull(cfg, "Config must not be null");
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir must not be null");

        try {
            this.mem = new MemTable();
            this.wal = new WAL(dataDir.resolve("wal.log"), cfg);
            this.sst = new SSTable(dataDir, cfg);
            this.compactor = new Compactor(sst, cfg);
            this.writeQueue = new WriteQueue(cfg.getWriteQueueCapacity());
            this.replicator = replicator; // use the injected one
        } catch (RuntimeException re) {
            throw new IOException("Failed to initialize StorageEngine components", re);
        }
    }


    public void start() throws IOException {
        ensureNotClosed();
        if (!started.compareAndSet(false, true)) return;

        try {
            wal.open();
        } catch (IOException ioe) {
            throw new IOException("Failed to open WAL: " + wal, ioe);
        }

        try {
            mem.recoverFromWAL(wal);
        } catch (RuntimeException re) {
            // If recovery fails, the engine is in an unknown state—close WAL and rethrow
            safeCloseWal();
            throw new IOException("MemTable recovery from WAL failed", re);
        }

        try {
            compactor.start();
        } catch (RuntimeException re) {
            // Non-fatal for basic operations, but report clearly
            throw new IOException("Failed to start compactor", re);
        }
    }

    public void put(String key, byte[] value) throws IOException {
        ensureReady();
        final String k = validateKey(key);
        final byte[] v = value == null ? new byte[0] : value;

        try {
            // Enqueue write; the queue implementation may execute synchronously or via a worker.


            writeQueue.enqueue(() -> {
                try {
                    wal.appendPut(k, v);
                    mem.put(k, v);
                    sst.write(k, v);
                    compactor.maybeSchedule();
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                } catch (RuntimeException re) {
                    throw re;
                }
            });
        } catch (UncheckedIOException uioe) {
            throw new IOException("PUT failed for key=" + k + ": WAL/memtable error", uioe.getCause());
        } catch (IllegalStateException ise) {
            // e.g., queue shut down or bounded capacity policy rejects
            throw new IOException("PUT rejected for key=" + k + ": " + ise.getMessage(), ise);
        } catch (RuntimeException re) {
            throw new IOException("PUT failed for key=" + k + ": " + re.getMessage(), re);
        }

        // Replication should not fail the primary write; log if present.
        if (replicator != null) {
            try {
                replicator.put(k, v);
            } catch (RuntimeException re) {
                // In a real system, route to a DLQ/repair log; here we log to stderr.
                System.err.println("[WARN] Replication PUT failed for key=" + k + ": " + re.getMessage());
            }
        }
    }

    public byte[] get(String key) throws IOException {
        ensureReady();
        final String k = validateKey(key);

        try {
            byte[] val = mem.get(k);
            if (val != null) return val;
            //return sst.get(k);
            return replicator.get(k);
        } catch (RuntimeException re) {
            throw new IOException("GET failed for key=" + k + ": " + re.getMessage(), re);
        }
    }

    public void delete(String key) throws IOException {
        ensureReady();
        final String k = validateKey(key);

        try {
            writeQueue.enqueue(() -> {
                try {
                    wal.appendDel(k);
                    mem.delete(k);
                    compactor.maybeSchedule();
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                } catch (RuntimeException re) {
                    throw re;
                }
            });
        } catch (UncheckedIOException uioe) {
            throw new IOException("DELETE failed for key=" + k + ": WAL/memtable error", uioe.getCause());
        } catch (IllegalStateException ise) {
            throw new IOException("DELETE rejected for key=" + k + ": " + ise.getMessage(), ise);
        } catch (RuntimeException re) {
            throw new IOException("DELETE failed for key=" + k + ": " + re.getMessage(), re);
        }

        if (replicator != null) {
            try {
               // replicator.replicateDel(k);
            } catch (RuntimeException re) {
                System.err.println("[WARN] Replication DELETE failed for key=" + k + ": " + re.getMessage());
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            // Best-effort shutdown; collect first failure to report
            IOException first = null;

            try {
                compactor.stop();
            } catch (RuntimeException re) {
                first = wrap(first, new IOException("Failed to stop compactor", re));
            }

            try {
                writeQueue.shutdown();
            } catch (RuntimeException re) {
                first = wrap(first, new IOException("Failed to shutdown write queue", re));
            }

            try {
                wal.close();
            } catch (IOException ioe) {
                first = wrap(first, new IOException("Failed to close WAL", ioe));
            }

            if (first != null) throw first;
        }
    }

    // -------------------- Helpers --------------------

    private void ensureReady() throws IOException {
        ensureNotClosed();
        if (!started.get()) {
            throw new IOException("StorageEngine not started. Call start() before use.");
        }
    }

    private void ensureNotClosed() throws IOException {
        if (closed.get()) {
            throw new IOException("StorageEngine is closed");
        }
    }

    private static String validateKey(String key) throws IOException {
        if (key == null) throw new IOException("Key must not be null");
        String k = key.trim();
        if (k.isEmpty()) throw new IOException("Key must not be empty");
        // Optional: enforce a max key size to avoid pathological inputs
        if (k.length() > 1024) throw new IOException("Key too large (max 1024 chars)");
        return k;
    }

    private void safeCloseWal() {
        try { wal.close(); } catch (IOException ignore) { /* best effort */ }
    }

    private static IOException wrap(IOException existing, IOException next) {
        if (existing == null) return next;
        existing.addSuppressed(next);
        return existing;
    }

    public void putBatch(Map<String, byte[]> entries) throws IOException {
        ensureReady();

        try {
            writeQueue.enqueue(() -> {
                try {
                    for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                        final String k = validateKey(entry.getKey());
                        final byte[] v = entry.getValue() == null ? new byte[0] : entry.getValue();

                        wal.appendPut(k, v);
                        mem.put(k, v);
                        sst.write(k, v);
                    }
                    compactor.maybeSchedule();
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                } catch (RuntimeException re) {
                    throw re;
                }
            });
        } catch (UncheckedIOException uioe) {
            throw new IOException("PUT_BATCH failed: WAL/memtable error", uioe.getCause());
        } catch (IllegalStateException ise) {
            throw new IOException("PUT_BATCH rejected: " + ise.getMessage(), ise);
        } catch (RuntimeException re) {
            throw new IOException("PUT_BATCH failed: " + re.getMessage(), re);
        }

        // Replication should not fail the primary write
        if (replicator != null) {
            try {
                replicator.putBatch(entries);
            } catch (RuntimeException re) {
                System.err.println("[WARN] Replication PUT_BATCH failed: " + re.getMessage());
            }
        }
    }

    public Map<String, byte[]> getBatch(List<String> keys) throws IOException {
        ensureReady();
        Map<String, byte[]> results = new HashMap<>();

        try {
            for (String key : keys) {
                final String k = validateKey(key);
                byte[] val = mem.get(k);

                if (val != null) {
                    results.put(k, val);
                } else {
                    // try replicator first
                    byte[] replVal = replicator != null ? replicator.get(k) : null;
                    if (replVal != null) {
                        results.put(k, replVal);
                    } else {
                        // fallback SSTable (if implemented)
                        // byte[] sstVal = sst.get(k);
                        // if (sstVal != null) results.put(k, sstVal);
                    }
                }
            }
        } catch (RuntimeException re) {
            throw new IOException("GET_BATCH failed: " + re.getMessage(), re);
        }

        return results;
    }

    public Map<String, byte[]> getRange(String startKey, String endKey) throws IOException {
        ensureReady();
        try {
            if (replicator != null) {
                return replicator.getRange(startKey, endKey);
            } else {
                return new HashMap<>(); // no replicator configured
            }
        } catch (RuntimeException re) {
            throw new IOException("GET_RANGE failed: " + re.getMessage(), re);
        }
    }

}
