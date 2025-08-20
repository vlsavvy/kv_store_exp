// TODO: Implement RangeHandler.java
package lsmkv.network.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lsmkv.engine.StorageEngine;

import java.io.IOException;

public class RangeHandler implements HttpHandler {
    private final StorageEngine engine;
    public RangeHandler(StorageEngine engine) { this.engine = engine; }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type","text/plain");
        ex.sendResponseHeaders(200, 0);
        ex.getResponseBody().write("Range Handler placeholder".getBytes());
        ex.close();
    }
}
