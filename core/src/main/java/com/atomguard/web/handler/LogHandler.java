package com.atomguard.web.handler;

import com.atomguard.AtomGuard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * /api/logs endpoint — son log satirlarini JSON olarak dondurur (sayfalanmis).
 */
public class LogHandler implements HttpHandler {

    private final AtomGuard plugin;

    public LogHandler(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Parse query params: ?limit=50&offset=0
        String query = exchange.getRequestURI().getQuery();
        int limit = 50;
        int offset = 0;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    if ("limit".equals(kv[0])) {
                        try { limit = Math.min(Integer.parseInt(kv[1]), 200); } catch (NumberFormatException ignored) {}
                    }
                    if ("offset".equals(kv[0])) {
                        try { offset = Math.max(Integer.parseInt(kv[1]), 0); } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        // Find today's log file
        String logFolderPath = plugin.getConfig().getString("general.log.folder", "logs/atomguard");
        File logFolder = new File(plugin.getDataFolder().getParentFile().getParentFile(), logFolderPath);
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        File logFile = new File(logFolder, "atomguard-" + today + ".log");

        StringBuilder json = new StringBuilder("{\"lines\":[");
        int total = 0;

        if (logFile.exists()) {
            List<String> allLines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
            total = allLines.size();
            // Reverse to get newest first
            Collections.reverse(allLines);
            int end = Math.min(offset + limit, allLines.size());
            boolean first = true;
            for (int i = offset; i < end; i++) {
                if (!first) json.append(",");
                first = false;
                json.append("\"").append(escapeJson(allLines.get(i))).append("\"");
            }
        }
        json.append("],\"total\":").append(total);
        json.append(",\"limit\":").append(limit);
        json.append(",\"offset\":").append(offset).append("}");

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
