package com.atomguard.velocity.metrics;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public class PrometheusExporter {

    private final AtomGuardVelocity plugin;
    private HttpServer httpServer;

    public PrometheusExporter(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    public void start(int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/metrics", exchange -> {
            String metrics = generateMetrics();
            byte[] response = metrics.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        httpServer.setExecutor(Executors.newSingleThreadExecutor());
        httpServer.start();
        plugin.getSlf4jLogger().info("Prometheus metrik sunucusu {} portunda başlatıldı.", port);
    }

    private String generateMetrics() {
        StringBuilder sb = new StringBuilder();

        // Online Oyuncu
        gauge(sb, "atomguard_players_online", "Anlik cevrimici oyuncu sayisi",
              plugin.getProxyServer().getPlayerCount());

        // Saldiri Modu
        gauge(sb, "atomguard_attack_mode", "Saldiri modu aktiflik durumu",
              plugin.isAttackMode() ? 1 : 0);
        
        // CPS (Connections Per Second)
        if (plugin.getDdosModule() != null && plugin.getDdosModule().getSynFloodDetector() != null) {
            gauge(sb, "atomguard_network_cps", "Saniyelik baglanti hizi",
                  plugin.getDdosModule().getSynFloodDetector().getCurrentRate());
        }

        // Modül İstatistikleri
        plugin.getModuleManager().getAll().forEach(m -> {
            counter(sb, "atomguard_module_blocked_total",
                    "Modul tarafindan engellenen toplam baglanti", 
                    Map.of("module", m.getName()), m.getBlockedCount());
        });

        // Genel İstatistikler
        plugin.getStatisticsManager().getAll().forEach((key, value) -> {
            counter(sb, "atomguard_stats_" + key.replace("-", "_").replace(".", "_"),
                    "Sistem istatistigi: " + key, Map.of(), value);
        });

        // JVM & System
        Runtime rt = Runtime.getRuntime();
        gauge(sb, "atomguard_jvm_memory_used_bytes", "Kullanilan JVM bellegi",
              rt.totalMemory() - rt.freeMemory());
        gauge(sb, "atomguard_jvm_memory_max_bytes", "Maksimum JVM bellegi",
              rt.maxMemory());
        
        try {
            java.lang.management.OperatingSystemMXBean os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            gauge(sb, "atomguard_system_load_average", "Sistem yuk ortalamasi",
                  (long) (os.getSystemLoadAverage() * 100)); // Prometheus gauge tam sayı tercih ederse *100
        } catch (Exception ignored) {}

        return sb.toString();
    }

    private void gauge(StringBuilder sb, String name, String help, long value) {
        sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
        sb.append("# TYPE ").append(name).append(" gauge\n");
        sb.append(name).append(" ").append(value).append("\n");
    }

    private void counter(StringBuilder sb, String name, String help, Map<String, String> labels, long value) {
        sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
        sb.append("# TYPE ").append(name).append(" counter\n");
        sb.append(name);
        if (!labels.isEmpty()) {
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                if (!first) sb.append(",");
                sb.append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"");
                first = false;
            }
            sb.append("}");
        }
        sb.append(" ").append(value).append("\n");
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }
}
