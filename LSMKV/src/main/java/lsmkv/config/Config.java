// TODO: Implement Config.java
package src.main.java.lsmkv.config;

import java.time.Duration;



public class Config {
    private final long memtableFlushBytes;
    private final Duration fsyncInterval;
    private final int sparseIndexEvery;
    private final int compactionFanIn;
    private final int writeQueueCapacity;




    public Config(long memtableFlushBytes, Duration fsyncInterval,
                  int sparseIndexEvery, int compactionFanIn, int writeQueueCapacity) {
        this.memtableFlushBytes = memtableFlushBytes;
        this.fsyncInterval = fsyncInterval;
        this.sparseIndexEvery = sparseIndexEvery;
        this.compactionFanIn = compactionFanIn;
        this.writeQueueCapacity = writeQueueCapacity;
    }

    public long getMemtableFlushBytes() { return memtableFlushBytes; }
    public Duration getFsyncInterval() { return fsyncInterval; }
    public int getSparseIndexEvery() { return sparseIndexEvery; }
    public int getCompactionFanIn() { return compactionFanIn; }
    public int getWriteQueueCapacity() { return writeQueueCapacity; }
}
