package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AbuseIPDBProvider {

    private final AtomGuardVelocity plugin;
    private final HttpClient httpClient;
    private final String apiKey;

    public AbuseIPDBProvider(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.apiKey = plugin.getConfigManager().getString("vpn-proxy-engelleme.abuseipdb.api-anahtari", "");
    }

    public CompletableFuture<Integer> check(String ip) {
        if (apiKey.isEmpty()) return CompletableFuture.completedFuture(0);

        String url = "https://api.abuseipdb.com/api/v2/check?ipAddress=" + ip + "&maxAgeInDays=30";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Key", apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            // Simple parsing to avoid full JSON lib dependency if not needed, or use Gson if available
                            String body = response.body();
                            int index = body.indexOf("\"abuseConfidenceScore\":");
                            if (index != -1) {
                                String scoreStr = body.substring(index + 23);
                                int endIndex = scoreStr.indexOf(",");
                                if (endIndex == -1) endIndex = scoreStr.indexOf("}");
                                return Integer.parseInt(scoreStr.substring(0, endIndex).trim());
                            }
                        } catch (Exception e) {
                            plugin.getSlf4jLogger().warn("Failed to parse AbuseIPDB response: " + e.getMessage());
                        }
                    }
                    return 0;
                })
                .exceptionally(e -> 0);
    }
}