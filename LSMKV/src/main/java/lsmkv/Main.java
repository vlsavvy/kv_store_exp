// Main.java
package src.main.java.lsmkv;

import com.sun.net.httpserver.HttpServer;
import src.main.java.lsmkv.config.Config;
import src.main.java.lsmkv.network.HttpServerWrapper;
import src.main.java.lsmkv.replication.Replicator;
import src.main.java.lsmkv.engine.StorageEngine;
import src.main.java.lsmkv.network.handlers.KvHandler;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws Exception {
        String dataDir = "./data";
        int port = 8080;


         long memtableFlushBytes = 8L * 1024 * 1024; // 8 MiB
         Duration fsyncInterval = Duration.ofMillis(50);
         int sparseIndexEvery = 32;
         int compactionFanIn = 4;
         int writeQueueCapacity = 1000; // backpressure queue

        // initialize config with defaults
        Config cfg = new Config(memtableFlushBytes,fsyncInterval,sparseIndexEvery,compactionFanIn,writeQueueCapacity);

        // Create replicator and storage engine
        Replicator replicator = new Replicator(Path.of(dataDir));
        StorageEngine engine = new StorageEngine(Path.of(dataDir),cfg,replicator);
        engine.start();

        //HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        //server.createContext("/
        //server.start();

        HttpServerWrapper serverWrapper = new HttpServerWrapper(engine,8080, Executors.newFixedThreadPool(10));
        serverWrapper.start();

        System.out.println("LSMKV listening on :" + port + "  dataDir=" + dataDir);
    }
}
