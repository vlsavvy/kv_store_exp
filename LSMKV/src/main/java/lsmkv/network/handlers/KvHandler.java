// TODO: Implement KvHandler.java
package lsmkv.network.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lsmkv.engine.StorageEngine;

import java.io.IOException;

public class KvHandler implements HttpHandler {
    private final StorageEngine engine;
    public KvHandler(StorageEngine engine) { this.engine = engine; }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type","text/plain");
        ex.sendResponseHeaders(200, 0);
        ex.getResponseBody().write("KV Handler placeholder".getBytes());
        ex.close();
    }
}
