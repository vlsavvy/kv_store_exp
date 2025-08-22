// TODO: Implement Compactor.java
package src.main.java.lsmkv.engine;

import src.main.java.lsmkv.config.Config;

public class Compactor {
    private final SSTable sst;
    private final Config cfg;

    public Compactor(SSTable sst, Config cfg) {
        this.sst = sst;
        this.cfg = cfg;
    }

    public void start() {
        // TODO: background compaction
    }

    public void maybeSchedule() {
        // TODO: trigger compaction if needed
    }

    public void stop() {
        // TODO: stop background compaction
    }
}


