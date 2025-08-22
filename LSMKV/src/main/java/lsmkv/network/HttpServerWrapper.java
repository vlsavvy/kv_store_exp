// TODO: Implement HttpServerWrapper.java
package src.main.java.lsmkv.network;

import com.sun.net.httpserver.HttpServer;
import src.main.java.lsmkv.engine.StorageEngine;
import src.main.java.lsmkv.network.handlers.BatchHandler;
import src.main.java.lsmkv.network.handlers.KvHandler;
import src.main.java.lsmkv.network.handlers.RangeHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

public class HttpServerWrapper {
    private final HttpServer server;

    public HttpServerWrapper(StorageEngine engine, int port, Executor executor) throws Exception {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/kv", new KvHandler(engine));
        server.createContext("/batch", new BatchHandler(engine));
        server.createContext("/range", new RangeHandler(engine));
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }
}
