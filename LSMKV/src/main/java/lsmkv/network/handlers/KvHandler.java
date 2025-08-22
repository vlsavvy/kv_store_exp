package src.main.java.lsmkv.network.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import src.main.java.lsmkv.engine.StorageEngine;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class KvHandler implements HttpHandler {
    private final StorageEngine engine;

    public KvHandler(StorageEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        URI uri = ex.getRequestURI();
        String path = uri.getPath(); // e.g. /kv/mykey
        String[] parts = path.split("/");
        String key = parts.length > 2 ? parts[2] : null;

        String response;
        int status;

        try {
            switch (method) {
                case "PUT": {
                    if (key == null || key.isEmpty()) {
                        status = 400;
                        response = "Missing key";
                        break;
                    }
                    byte[] value = ex.getRequestBody().readAllBytes();
                    engine.put(key, value);
                    status = 200;
                    response = "OK";
                    break;
                }

                case "GET": {
                    if (key == null || key.isEmpty()) {
                        status = 400;
                        response = "Missing key";
                        break;
                    }
                    byte[] stored = engine.get(key);
                    if (stored != null) {
                        status = 200;
                        response = new String(stored, StandardCharsets.UTF_8);
                    } else {
                        status = 404;
                        response = "Key not found";
                    }
                    break;
                }

                case "DELETE": {
                    if (key == null || key.isEmpty()) {
                        status = 400;
                        response = "Missing key";
                        break;
                    }
                    engine.delete(key);
                    status = 200;
                    response = "Deleted";
                    break;
                }

                default:
                    status = 405;
                    response = "Method Not Allowed";
                    break;
            }
        } catch (Exception e) {
            status = 500;
            response = "Error: " + e.getMessage();
        }

        ex.getResponseHeaders().set("Content-Type", "text/plain");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
