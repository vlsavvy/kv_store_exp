// TODO: Implement SSTable.java
package lsmkv.engine;

import lsmkv.config.Config;

import java.io.IOException;
import java.nio.file.Path;

public class SSTable {
    private final Path dataDir;
    private final Config cfg;

    public SSTable(Path dataDir, Config cfg) {
        this.dataDir = dataDir;
        this.cfg = cfg;
    }

    public byte[] get(String key) {
        // TODO: search SSTables
        return null;
    }
}
