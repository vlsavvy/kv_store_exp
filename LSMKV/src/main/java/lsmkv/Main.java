// TODO: Implement Main.java
package lsmkv;

import com.sun.net.httpserver.HttpServer;
import lsmkv.config.Config;
import lsmkv.engine.StorageEngine;
import lsmkv.network.HttpServerWrapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) args = new String[]{"./data"};

        Path dataDir = Paths.get(args[0]);
        Files.createDirectories(dataDir);

        Config cfg = new Config();
        StorageEngine engine = new StorageEngine(dataDir, cfg);
        engine.start();

        HttpServerWrapper server = new HttpServerWrapper(engine, 8080, Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        server.start();

        System.out.printf("LSMKV listening on :8080  dataDir=%s%n", dataDir.toAbsolutePath());
    }
}
