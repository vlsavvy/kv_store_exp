// TODO: Implement WAL.java
package src.main.java.lsmkv.engine;

import src.main.java.lsmkv.config.Config;

import java.io.IOException;
import java.nio.file.Path;

public class WAL {
    private final Path path;
    private final Config cfg;

    public WAL(Path path, Config cfg) {
        this.path = path;
        this.cfg = cfg;
    }

    public void open() throws IOException {
        // TODO: open WAL file
    }

    public void appendPut(String key, byte[] value) throws IOException {
        // TODO: write put to WAL
    }

    public void appendDel(String key) throws IOException {
        // TODO: write delete to WAL
    }

    public void close() throws IOException {
        // TODO: close WAL file
    }
}
