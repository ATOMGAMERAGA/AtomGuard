package com.atomguard.web.handler;

import com.atomguard.AtomGuard;
import com.atomguard.web.WebPanel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Central API handler providing /api/health and /api/dashboard endpoints.
 * Health returns server metrics (TPS, memory, uptime, online players).
 * Dashboard returns summary statistics for the web dashboard.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class ApiHandler implements HttpHandler {

    private final AtomGuard plugin;
    private final WebPanel webPanel;
    private final long startTime;

    public ApiHandler(AtomGuard plugin, WebPanel webPanel) {
        this.plugin = plugin;
        this.webPanel = webPanel;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if ("/api/health".equals(path)) {
            handleHealth(exchange);
        } else if ("/api/dashboard".equals(path)) {
            handleDashboard(exchange);
        } else if ("/api/metrics".equals(path)) {
            handleMetrics(exchange);
        } else {
            sendResponse(exchange, 404, "application/json", "{\"error\":\"Not found\"}");
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
        long totalMemoryMB = runtime.totalMemory() / (1024 * 1024);
        long freeMemoryMB = runtime.freeMemory() / (1024 * 1024);
        long usedMemoryMB = totalMemoryMB - freeMemoryMB;
        long uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000;
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();

        // TPS - Bukkit'ten son 1 dakika TPS
        double tps;
        try {
            tps = Bukkit.getTPS()[0]; // Paper API: 1m average
        } catch (Exception e) {
            tps = -1.0;
        }

        String json = String.format(
            "{\"status\":\"online\",\"tps\":%.2f,\"memory\":{\"used_mb\":%d,\"total_mb\":%d,\"max_mb\":%d,\"free_mb\":%d},"
            + "\"uptime_seconds\":%d,\"online_players\":%d,\"max_players\":%d,"
            + "\"java_version\":\"%s\",\"server_version\":\"%s\"}",
            tps, usedMemoryMB, totalMemoryMB, maxMemoryMB, freeMemoryMB,
            uptimeSeconds, onlinePlayers, maxPlayers,
            escapeJson(System.getProperty("java.version", "unknown")),
            escapeJson(Bukkit.getVersion()));

        sendResponse(exchange, 200, "application/json", json);
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        long blockedTotal = plugin.getModuleManager().getTotalBlockedCount();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        boolean isAttack = plugin.getAttackModeManager().isAttackMode();
        int connectionRate = plugin.getAttackModeManager().getCurrentRate();
        int enabledModules = plugin.getModuleManager().getEnabledModuleCount();
        int totalModules = plugin.getModuleManager().getTotalModuleCount();

        long allTimeBlocked = 0;
        if (plugin.getStatisticsManager() != null) {
            allTimeBlocked = plugin.getStatisticsManager().getTotalBlockedAllTime();
        }

        int recentEventCount = webPanel.getRecentEvents().size();

        // Uptime
        long uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000;

        // Memory
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);

        String json = String.format(
            "{\"blocked_total\":%d,\"blocked_all_time\":%d,\"online_players\":%d,"
            + "\"attack_mode\":%b,\"connection_rate\":%d,"
            + "\"modules_active\":%d,\"modules_total\":%d,"
            + "\"recent_event_count\":%d,\"uptime_seconds\":%d,"
            + "\"memory_used_mb\":%d,\"memory_max_mb\":%d}",
            blockedTotal, allTimeBlocked, onlinePlayers,
            isAttack, connectionRate,
            enabledModules, totalModules,
            recentEventCount, uptimeSeconds,
            usedMemoryMB, maxMemoryMB);

        sendResponse(exchange, 200, "application/json", json);
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (plugin.getCoreMetrics() != null && plugin.getCoreMetrics().isEnabled()) {
            sendResponse(exchange, 200, "application/json", plugin.getCoreMetrics().toJson());
        } else {
            sendResponse(exchange, 200, "application/json", "{\"error\":\"Metrics disabled\"}");
        }
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
