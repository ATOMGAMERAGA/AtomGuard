package com.atomguard.web.handler;

import com.atomguard.AtomGuard;
import com.atomguard.module.AbstractModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * Bearer-token korumalı metrik endpoint'i.
 *
 * <p>İki format desteklenir:
 * <ul>
 *   <li>{@code GET /metrics} → JSON (default, Grafana JSON datasource için)</li>
 *   <li>{@code GET /metrics?format=prom} → Prometheus exposition format</li>
 * </ul>
 *
 * <p>Auth:
 * <ul>
 *   <li>{@code Authorization: Bearer <token>} header</li>
 *   <li>Veya {@code ?token=<token>} query param</li>
 * </ul>
 * Token {@code web-panel.metrics-token} config key'inden okunur. Boş ise
 * endpoint aktif olmaz (401 döner).
 *
 * @author AtomGuard Team
 * @version 2.3.0
 */
public class MetricsHandler implements HttpHandler {

    private final AtomGuard plugin;
    private final long startedAt = System.currentTimeMillis();

    public MetricsHandler(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
            return;
        }

        // Auth kontrolü
        String configuredToken = plugin.getConfig().getString("web-panel.metrics-token", "");
        if (configuredToken == null || configuredToken.isBlank()) {
            respond(exchange, 401, "application/json", "{\"error\":\"metrics-token not configured\"}");
            return;
        }

        String provided = extractToken(exchange);
        if (provided == null || !provided.equals(configuredToken)) {
            respond(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return;
        }

        // Format seçimi
        String query = exchange.getRequestURI().getQuery();
        boolean prometheus = query != null && query.contains("format=prom");

        if (prometheus) {
            respond(exchange, 200, "text/plain; version=0.0.4", buildPrometheus());
        } else {
            respond(exchange, 200, "application/json", buildJson());
        }
    }

    private String extractToken(HttpExchange ex) {
        // Header
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        // Query
        String query = ex.getRequestURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && kv[0].equals("token")) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private String buildJson() {
        Runtime rt = Runtime.getRuntime();
        long uptime = (System.currentTimeMillis() - startedAt) / 1000;

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"uptime_seconds\":").append(uptime).append(",");
        sb.append("\"jvm\":{");
        sb.append("\"memory_used_mb\":").append((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024).append(",");
        sb.append("\"memory_total_mb\":").append(rt.totalMemory() / 1024 / 1024).append(",");
        sb.append("\"memory_max_mb\":").append(rt.maxMemory() / 1024 / 1024).append(",");
        sb.append("\"available_processors\":").append(rt.availableProcessors());
        sb.append("},");
        sb.append("\"server\":{");
        sb.append("\"online_players\":").append(plugin.getServer().getOnlinePlayers().size()).append(",");
        sb.append("\"max_players\":").append(plugin.getServer().getMaxPlayers()).append(",");
        sb.append("\"tps\":").append(jsonNumber(plugin.getServer().getTPS()[0])).append(",");
        sb.append("\"attack_mode\":").append(plugin.getAttackModeManager().isAttackMode()).append(",");
        sb.append("\"blocked_during_attack\":").append(plugin.getAttackModeManager().getBlockedDuringAttack()).append(",");
        sb.append("\"verified_ips\":").append(plugin.getAttackModeManager().getVerifiedIpCount()).append(",");
        sb.append("\"whitelist_size\":").append(plugin.getWhitelistManager() != null
                ? plugin.getWhitelistManager().size() : 0);
        sb.append("},");

        // Modüller
        sb.append("\"modules\":{");
        Collection<AbstractModule> modules = plugin.getModuleManager().getAllModules();
        boolean first = true;
        long totalBlocked = 0;
        int enabledCount = 0;
        for (AbstractModule m : modules) {
            if (!first) sb.append(",");
            sb.append("\"").append(m.getName()).append("\":{");
            sb.append("\"enabled\":").append(m.isEnabled()).append(",");
            sb.append("\"blocked\":").append(m.getBlockedCount());
            sb.append("}");
            totalBlocked += m.getBlockedCount();
            if (m.isEnabled()) enabledCount++;
            first = false;
        }
        sb.append("},");
        sb.append("\"summary\":{");
        sb.append("\"total_modules\":").append(modules.size()).append(",");
        sb.append("\"enabled_modules\":").append(enabledCount).append(",");
        sb.append("\"total_blocked_all_time\":").append(totalBlocked);
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    private String buildPrometheus() {
        Runtime rt = Runtime.getRuntime();
        long uptime = (System.currentTimeMillis() - startedAt) / 1000;
        StringBuilder sb = new StringBuilder();

        // JVM
        sb.append("# HELP atomguard_uptime_seconds Plugin uptime\n");
        sb.append("# TYPE atomguard_uptime_seconds counter\n");
        sb.append("atomguard_uptime_seconds ").append(uptime).append("\n");

        sb.append("# HELP atomguard_jvm_memory_bytes JVM memory usage\n");
        sb.append("# TYPE atomguard_jvm_memory_bytes gauge\n");
        sb.append("atomguard_jvm_memory_bytes{type=\"used\"} ").append(rt.totalMemory() - rt.freeMemory()).append("\n");
        sb.append("atomguard_jvm_memory_bytes{type=\"total\"} ").append(rt.totalMemory()).append("\n");
        sb.append("atomguard_jvm_memory_bytes{type=\"max\"} ").append(rt.maxMemory()).append("\n");

        // Server
        sb.append("# HELP atomguard_players_online Currently online players\n");
        sb.append("# TYPE atomguard_players_online gauge\n");
        sb.append("atomguard_players_online ").append(plugin.getServer().getOnlinePlayers().size()).append("\n");

        sb.append("# HELP atomguard_tps Server TPS\n");
        sb.append("# TYPE atomguard_tps gauge\n");
        sb.append("atomguard_tps ").append(plugin.getServer().getTPS()[0]).append("\n");

        sb.append("# HELP atomguard_attack_mode Whether attack mode is active\n");
        sb.append("# TYPE atomguard_attack_mode gauge\n");
        sb.append("atomguard_attack_mode ").append(plugin.getAttackModeManager().isAttackMode() ? 1 : 0).append("\n");

        sb.append("# HELP atomguard_blocked_during_attack Total blocked connections during current/last attack\n");
        sb.append("# TYPE atomguard_blocked_during_attack counter\n");
        sb.append("atomguard_blocked_during_attack ").append(plugin.getAttackModeManager().getBlockedDuringAttack()).append("\n");

        // Modüller
        sb.append("# HELP atomguard_module_blocked_total Total blocked events per module\n");
        sb.append("# TYPE atomguard_module_blocked_total counter\n");
        for (AbstractModule m : plugin.getModuleManager().getAllModules()) {
            sb.append("atomguard_module_blocked_total{module=\"").append(m.getName())
                    .append("\",enabled=\"").append(m.isEnabled()).append("\"} ")
                    .append(m.getBlockedCount()).append("\n");
        }

        return sb.toString();
    }

    private String jsonNumber(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
        return String.format(java.util.Locale.US, "%.2f", d);
    }

    private void respond(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
