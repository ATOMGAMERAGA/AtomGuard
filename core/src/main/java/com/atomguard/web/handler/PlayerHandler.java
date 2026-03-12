package com.atomguard.web.handler;

import com.atomguard.AtomGuard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * /api/players endpoint — online oyuncu listesi ve trust skorlari.
 */
public class PlayerHandler implements HttpHandler {

    private final AtomGuard plugin;

    public PlayerHandler(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!first) json.append(",");
            first = false;

            int trustScore = 0;
            String tier = "NEW";
            if (plugin.getTrustScoreManager() != null) {
                trustScore = plugin.getTrustScoreManager().getScore(player.getUniqueId());
                tier = plugin.getTrustScoreManager().getTier(player.getUniqueId()).name();
            }

            String ip = "unknown";
            if (player.getAddress() != null && player.getAddress().getAddress() != null) {
                ip = player.getAddress().getAddress().getHostAddress();
            }

            json.append(String.format(
                "{\"uuid\":\"%s\",\"name\":\"%s\",\"ip\":\"%s\",\"trust_score\":%d,\"tier\":\"%s\"}",
                player.getUniqueId(), escapeJson(player.getName()), escapeJson(ip), trustScore, tier));
        }
        json.append("]");

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
