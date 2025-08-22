package src.main.java.lsmkv.network.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import src.main.java.lsmkv.engine.StorageEngine;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RangeHandler implements HttpHandler {
    private final StorageEngine engine;

    public RangeHandler(StorageEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String response;
        int status;

        try {
            String query = ex.getRequestURI().getQuery();
            if (query == null || !query.contains("start=") || !query.contains("end=")) {
                status = 400;
                response = "Missing start or end parameters";
            } else {
                String start = getParam(query, "start");
                String end = getParam(query, "end");

                Map<String, byte[]> rangeResult;
                try {
                    rangeResult = engine.getRange(start, end);
                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    int i = 0;
                    for (Map.Entry<String, byte[]> entry : rangeResult.entrySet()) {
                        sb.append("\"").append(entry.getKey()).append("\":");
                        if (entry.getValue() != null) {
                            sb.append("\"").append(new String(entry.getValue(), StandardCharsets.UTF_8)).append("\"");
                        } else {
                            sb.append("null");
                        }
                        if (i++ < rangeResult.size() - 1) sb.append(",");
                    }
                    sb.append("}");
                    response = sb.toString();
                    status = 200;
                } catch (Exception e) {
                    status = 500;
                    response = "Error retrieving range: " + e.getMessage();
                }
            }
        } catch (Exception e) {
            status = 500;
            response = "Unexpected server error: " + e.getMessage();
        }

        ex.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getParam(String query, String key) {
        for (String param : query.split("&")) {
            if (param.startsWith(key + "=")) {
                return param.substring((key + "=").length());
            }
        }
        return null;
    }
}
