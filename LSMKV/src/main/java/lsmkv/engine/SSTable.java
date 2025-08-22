package src.main.java.lsmkv.engine;

import src.main.java.lsmkv.config.Config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class SSTable {
    private final Path dataDir;
    private final Path tableFile;

    public SSTable(Path dataDir, Config cfg) throws IOException {
        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
        this.tableFile = dataDir.resolve("sstable.data");
        if (!Files.exists(tableFile)) {
            Files.createFile(tableFile);
        }
    }

    public synchronized void write(String key, byte[] value) throws IOException {
        String line = key + "=" + new String(value, StandardCharsets.UTF_8) + System.lineSeparator();
        Files.write(tableFile, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
    }

    public synchronized byte[] get(String key) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(tableFile, StandardCharsets.UTF_8)) {
            String line;
            String found = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(key + "=")) {
                    found = line.substring((key + "=").length());
                }
            }
            return found == null ? null : found.getBytes(StandardCharsets.UTF_8);
        }
    }
}
