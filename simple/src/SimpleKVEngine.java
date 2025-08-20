// SimpleKV.java
// Compact, standard-library-only persistent Key/Value store with HTTP API.
// Build: javac SimpleKV.java
// Run:   java SimpleKV 8080 ./data

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class SimpleKVEngine {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java SimpleKV <port> <dataDir>");
            System.exit(2);
        }
        int port = Integer.parseInt(args[0]);
        Path dataDir = Paths.get(args[1]);
        Files.createDirectories(dataDir);

        StorageEngine engine = new StorageEngine(dataDir);
        try {
            engine.start();
        } catch (IOException e) {
            System.err.println("Failed to start storage engine: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/kv", new KvHandler(engine));
        server.createContext("/range", new RangeHandler(engine));
        server.createContext("/batch", new BatchHandler(engine));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.printf("SimpleKV listening on :%d  dataDir=%s%n", port, dataDir.toAbsolutePath());
    }

    // ===========================
    // HTTP Handlers
    // ===========================
    static class KvHandler implements HttpHandler {
        private final StorageEngine engine;
        KvHandler(StorageEngine engine) { this.engine = engine; }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            URI uri = ex.getRequestURI();
            Map<String,String> q = parseQuery(uri.getRawQuery());
            String key = q.get("key");
            if (key == null || key.isEmpty()) {
                send(ex, 400, "missing key");
                return;
            }

            try {
                switch (ex.getRequestMethod()) {
                    case "PUT": {
                        byte[] v = readAll(ex.getRequestBody());
                        engine.put(key, v);
                        send(ex, 200, "OK");
                        return;
                    }
                    case "GET": {
                        byte[] v = engine.get(key);
                        if (v == null) { send(ex, 404, "NOT_FOUND"); return; }
                        sendBytes(ex, 200, "application/octet-stream", v);
                        return;
                    }
                    case "DELETE": {
                        engine.delete(key);
                        send(ex, 200, "OK");
                        return;
                    }
                    default:
                        send(ex, 405, "method not allowed");
                }
            } catch (IllegalArgumentException ia) {
                send(ex, 400, "bad request: " + ia.getMessage());
            } catch (IOException ioe) {
                send(ex, 500, "io error: " + ioe.getMessage());
            } catch (Exception e) {
                send(ex, 500, "error: " + e.getMessage());
            }
        }
    }

    static class RangeHandler implements HttpHandler {
        private final StorageEngine engine;
        RangeHandler(StorageEngine engine) { this.engine = engine; }
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());
            String start = q.getOrDefault("start", "");
            String end = q.getOrDefault("end", "\uFFFF");
            if (start.compareTo(end) > 0) { send(ex, 400, "start > end"); return; }

            try {
                SortedMap<String, Long> slice = engine.rangeIndex(start, end);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(200, 0);
                BufferedWriter w = new BufferedWriter(new OutputStreamWriter(ex.getResponseBody(), StandardCharsets.UTF_8));
                for (String k : slice.keySet()) {
                    byte[] val = engine.get(k);
                    if (val == null) continue; // deleted
                    w.write(k);
                    w.write('\t');
                    w.write(new String(val, StandardCharsets.UTF_8));
                    w.write('\n');
                }
                w.flush();
            } catch (IOException ioe) {
                send(ex, 500, "io error: " + ioe.getMessage());
            }
        }
    }

    static class BatchHandler implements HttpHandler {
        private final StorageEngine engine;
        BatchHandler(StorageEngine engine) { this.engine = engine; }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, "method not allowed"); return; }
            int count = 0;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8))) {
                String line;
                List<Map.Entry<String, byte[]>> batch = new ArrayList<>();
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    int tab = line.indexOf('\t');
                    if (tab < 0) continue;
                    String k = line.substring(0, tab);
                    byte[] v = line.substring(tab+1).getBytes(StandardCharsets.UTF_8);
                    batch.add(new AbstractMap.SimpleImmutableEntry<>(k, v));
                    count++;
                }
                engine.batchPut(batch);
                send(ex, 200, "OK " + count);
            } catch (IOException ioe) {
                send(ex, 500, "io error: " + ioe.getMessage());
            } catch (IllegalArgumentException ia) {
                send(ex, 400, "bad batch: " + ia.getMessage());
            }
        }
    }

    // ===========================
    // Storage Engine (compact)
    // ===========================
    static class StorageEngine {
        private final Path dir;
        private final Path logPath;
        private final Path indexPath;
        private RandomAccessFile logRaf;
        private FileChannel logChannel;
        // in-memory index: key -> file offset
        // TreeMap allows range queries
        private final TreeMap<String, Long> index = new TreeMap<>();
        private final Object writeLock = new Object();

        StorageEngine(Path dir) {
            this.dir = dir;
            this.logPath = dir.resolve("store.log");
            this.indexPath = dir.resolve("index.db");
        }

        void start() throws IOException {
            Files.createDirectories(dir);
            openLog();
            if (Files.exists(indexPath)) {
                try {
                    loadIndex();
                } catch (Exception e) {
                    System.err.println("index load failed, rebuilding from log: " + e.getMessage());
                    rebuildIndexFromLog();
                }
            } else {
                rebuildIndexFromLog();
            }
        }

        void openLog() throws FileNotFoundException {
            logRaf = new RandomAccessFile(logPath.toFile(), "rw");
            logChannel = logRaf.getChannel();
        }

        // Put: append a record and update in-memory index and index file
        void put(String key, byte[] value) throws IOException {
            if (key == null || key.isEmpty()) throw new IllegalArgumentException("empty key");
            if (value == null) value = new byte[0];
            synchronized (writeLock) {
                long pos = logChannel.size();
                // record format: [byte type][int klen][int vlen][key bytes][value bytes]
                ByteBuffer hdr = ByteBuffer.allocate(1 + 4 + 4);
                hdr.put((byte)0).putInt(key.getBytes(StandardCharsets.UTF_8).length).putInt(value.length).flip();
                logChannel.position(pos);
                logChannel.write(hdr);
                logChannel.write(ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8)));
                if (value.length > 0) logChannel.write(ByteBuffer.wrap(value));
                logChannel.force(true);
                index.put(key, pos);
                appendIndexEntry(key, pos);
            }
        }

        byte[] get(String key) throws IOException {
            Long pos;
            synchronized (writeLock) { pos = index.get(key); }
            if (pos == null) return null;
            return readValueAt(pos);
        }

        void delete(String key) throws IOException {
            if (key == null || key.isEmpty()) throw new IllegalArgumentException("empty key");
            synchronized (writeLock) {
                long pos = logChannel.size();
                byte[] kb = key.getBytes(StandardCharsets.UTF_8);
                ByteBuffer hdr = ByteBuffer.allocate(1 + 4 + 4);
                hdr.put((byte)1).putInt(kb.length).putInt(0).flip(); // tombstone type=1
                logChannel.position(pos);
                logChannel.write(hdr);
                logChannel.write(ByteBuffer.wrap(kb));
                logChannel.force(true);
                index.put(key, pos); // tombstone at this offset; get() will return null
                appendIndexEntry(key, pos);
            }
        }

        // Batch put optimized to write single append for many entries
        void batchPut(List<Map.Entry<String, byte[]>> entries) throws IOException {
            if (entries == null || entries.isEmpty()) return;
            synchronized (writeLock) {
                long pos = logChannel.size();
                logChannel.position(pos);
                for (Map.Entry<String, byte[]> e : entries) {
                    String k = e.getKey();
                    byte[] v = e.getValue(); if (v == null) v = new byte[0];
                    byte[] kb = k.getBytes(StandardCharsets.UTF_8);
                    ByteBuffer hdr = ByteBuffer.allocate(1 + 4 + 4);
                    hdr.put((byte)0).putInt(kb.length).putInt(v.length).flip();
                    logChannel.write(hdr);
                    logChannel.write(ByteBuffer.wrap(kb));
                    if (v.length > 0) logChannel.write(ByteBuffer.wrap(v));
                    index.put(k, pos);
                    appendIndexEntry(k, pos);
                    pos = logChannel.position();
                }
                logChannel.force(true);
            }
        }

        // Range index (snapshot)
        SortedMap<String, Long> rangeIndex(String start, String end) {
            synchronized (writeLock) {
                return index.subMap(start, true, end, true);
            }
        }

        // ------------------
        // On-disk index helpers
        // ------------------
        private void appendIndexEntry(String key, long offset) {
            // Append line "key\toffset\n" to indexPath. Open+append is simple and avoids keeping index file in memory.
            try (FileChannel idx = FileChannel.open(indexPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                String line = key + "\t" + offset + "\n";
                idx.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                idx.force(true);
            } catch (IOException e) {
                // best-effort: index file error shouldn't lose data in log; surface to stderr
                System.err.println("Failed to append index entry: " + e.getMessage());
            }
        }

        private void loadIndex() throws IOException {
            index.clear();
            try (BufferedReader r = Files.newBufferedReader(indexPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    int tab = line.lastIndexOf('\t');
                    if (tab <= 0) continue;
                    String k = line.substring(0, tab);
                    long off = Long.parseLong(line.substring(tab + 1));
                    index.put(k, off);
                }
            }
            // basic sanity: if log shorter than max offset, we will rebuild
            if (!index.isEmpty()) {
                long maxOff = index.values().stream().mapToLong(Long::longValue).max().orElse(0L);
                if (maxOff >= logChannel.size()) {
                    throw new IOException("index refers to offsets beyond log size; rebuild required");
                }
            }
        }

        private void rebuildIndexFromLog() throws IOException {
            index.clear();
            try (RandomAccessFile raf = new RandomAccessFile(logPath.toFile(), "r")) {
                long pos = 0;
                while (pos < raf.length()) {
                    raf.seek(pos);
                    int type;
                    try {
                        type = raf.readUnsignedByte();
                    } catch (EOFException eof) { break; }
                    int klen = raf.readInt();
                    int vlen = raf.readInt();
                    byte[] kb = new byte[klen];
                    raf.readFully(kb);
                    String key = new String(kb, StandardCharsets.UTF_8);
                    // move pointer past value
                    raf.seek(raf.getFilePointer() + vlen);
                    index.put(key, pos);
                    pos = raf.getFilePointer();
                }
            } catch (FileNotFoundException e) {
                // no log yet; ignore
            }
            // rewrite index file from scratch for speed next startup
            try (FileChannel idx = FileChannel.open(indexPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Long> e : index.entrySet()) {
                    sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
                }
                idx.write(ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8)));
                idx.force(true);
            } catch (IOException e) {
                System.err.println("Failed to write index file after rebuild: " + e.getMessage());
            }
        }

        private byte[] readValueAt(long pos) throws IOException {
            try {
                // read header
                ByteBuffer hdr = ByteBuffer.allocate(1 + 4 + 4);
                logChannel.read(hdr, pos);
                hdr.flip();
                byte type = hdr.get();
                int klen = hdr.getInt();
                int vlen = hdr.getInt();
                long valPos = pos + 1 + 4 + 4 + klen;
                if (type == 1) return null; // tombstone
                ByteBuffer vb = ByteBuffer.allocate(vlen);
                logChannel.read(vb, valPos);
                return vb.array();
            } catch (IOException e) {
                throw new IOException("failed to read value at " + pos + ": " + e.getMessage(), e);
            }
        }
    }

    // ===========================
    // Utilities: HTTP send/read
    // ===========================
    static Map<String,String> parseQuery(String raw) {
        Map<String,String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) { m.put(urlDecode(part), ""); continue; }
            String k = urlDecode(part.substring(0, eq));
            String v = urlDecode(part.substring(eq+1));
            m.put(k, v);
        }
        return m;
    }
    static String urlDecode(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8*1024];
        int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toByteArray();
    }
    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
    static void sendBytes(HttpExchange ex, int code, String ct, byte[] b) throws IOException {
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
}
