// TODO: Implement Utils.java
package src.main.java.lsmkv.util;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

public class Utils {
    public static void sendResponse(HttpExchange ex, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type","text/plain");
        ex.sendResponseHeaders(200, body.getBytes().length);
        ex.getResponseBody().write(body.getBytes());
        ex.close();
    }

    public static String parseQuery(HttpExchange ex) {
        return ex.getRequestURI().getQuery();
    }
}
