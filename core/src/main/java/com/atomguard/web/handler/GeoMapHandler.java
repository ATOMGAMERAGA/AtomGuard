package com.atomguard.web.handler;

import com.atomguard.AtomGuard;
import com.atomguard.web.WebPanel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * /api/geomap endpoint — son engelleme olaylarini koordinat bilgisiyle dondurur.
 */
public class GeoMapHandler implements HttpHandler {

    private final AtomGuard plugin;
    private final WebPanel webPanel;

    public GeoMapHandler(AtomGuard plugin, WebPanel webPanel) {
        this.plugin = plugin;
        this.webPanel = webPanel;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<WebPanel.EventRecord> events = webPanel.getRecentEvents();
        int maxEvents = plugin.getConfig().getInt("web-panel.geo-harita.max-olay", 500);

        StringBuilder json = new StringBuilder("{\"events\":[");
        boolean first = true;
        int count = 0;
        for (WebPanel.EventRecord ev : events) {
            if (count >= maxEvents) break;
            if (!first) json.append(",");
            first = false;
            json.append(String.format(
                "{\"player\":\"%s\",\"module\":\"%s\",\"details\":\"%s\",\"timestamp\":%d}",
                escapeJson(ev.playerName), escapeJson(ev.moduleName),
                escapeJson(ev.details), ev.timestamp));
            count++;
        }
        json.append("]}");

        sendResponse(exchange, 200, "application/json", json.toString());
    }

    private void sendResponse(HttpExchange exchange, int code, String contentType, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(code, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "")
                   .replace("\t", "    ");
    }
}
