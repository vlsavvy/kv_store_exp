package src.main.java.lsmkv.network.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import src.main.java.lsmkv.engine.StorageEngine;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class BatchHandler implements HttpHandler {
    private final StorageEngine engine;

    public BatchHandler(StorageEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        URI uri = ex.getRequestURI();
        String path = uri.getPath();
        String[] parts = path.split("/");
        String key = parts.length > 2 ? parts[2] : null;

        String response;
        int status;

        try {
            switch (method) {
                case "POST": { // batch insert
                    try {
                        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                        // parse JSON-like simple object manually (since no ObjectMapper)
                        Map<String, byte[]> kvPairs = parseJsonToMap(body);

                        engine.putBatch(kvPairs);
                        status = 200;
                        response = "Batch insert successful";
                    } catch (IOException e) {
                        status = 500;
                        response = "Error reading request body: " + e.getMessage();
                    } catch (IllegalArgumentException e) {
                        status = 400;
                        response = "Invalid request format: " + e.getMessage();
                    } catch (Exception e) {
                        status = 500;
                        response = "Unexpected error: " + e.getMessage();
                    }
                    break;
                }
                case "GET": {
                    try {
                        String query = ex.getRequestURI().getQuery();
                        if (query == null || !query.startsWith("keys=")) {
                            status = 400;
                            response = "Missing keys parameter (expected ?keys=key1,key2,...)";
                            break;
                        }

                        String keysParam = query.substring("keys=".length());
                        if (keysParam.isEmpty()) {
                            status = 400;
                            response = "Keys parameter cannot be empty";
                            break;
                        }

                        String[] keys = keysParam.split(",");
                        StringBuilder jsonBuilder = new StringBuilder();
                        jsonBuilder.append("{");

                        for (int i = 0; i < keys.length; i++) {
                            String k = keys[i];
                            String value = null;
                            try {
                                byte[] stored = engine.get(k);
                                if (stored != null) {
                                    value = new String(stored, StandardCharsets.UTF_8);
                                }
                            } catch (Exception e) {
                                value = "ERROR: " + e.getMessage();
                            }

                            jsonBuilder.append("\"").append(k).append("\":");
                            if (value == null) {
                                jsonBuilder.append("null");
                            } else {
                                // escape quotes in value for safety
                                jsonBuilder.append("\"")
                                        .append(value.replace("\"", "\\\""))
                                        .append("\"");
                            }

                            if (i < keys.length - 1) {
                                jsonBuilder.append(",");
                            }
                        }

                        jsonBuilder.append("}");
                        response = jsonBuilder.toString();
                        status = 200;

                    } catch (Exception e) {
                        status = 500;
                        response = "Server error while processing GET batch: " + e.getMessage();
                        e.printStackTrace();
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

    /**
     * Very simple JSON-like parser. Expects {"k1":"v1","k2":"v2"}
     */
    private Map<String, String> parseJsonLike(String body) {
        Map<String, String> map = new HashMap<>();
        body = body.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1); // remove { }
            String[] pairs = body.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String k = kv[0].trim().replaceAll("\"", "");
                    String v = kv[1].trim().replaceAll("\"", "");
                    map.put(k, v);
                }
            }
        }
        return map;
    }
    private Map<String, byte[]> parseJsonToMap(String json) {
        Map<String, byte[]> map = new HashMap<>();

        // Strip { } and split
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("^\"|\"$", "");
                String value = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(key, value.getBytes(StandardCharsets.UTF_8));
            }
        }

        return map;
    }
}
