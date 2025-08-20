// TODO: Implement Config.java
package lsmkv.config;

import java.time.Duration;

public class Config {
    public long memtableFlushBytes = 8L * 1024 * 1024; // 8 MiB
    public Duration fsyncInterval = Duration.ofMillis(50);
    public int sparseIndexEvery = 32;
    public int compactionFanIn = 4;
    public int writeQueueCapacity = 1000; // backpressure queue
}
