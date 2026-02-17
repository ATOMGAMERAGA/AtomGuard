package com.atomguard.velocity.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Ağ yardımcı metodları.
 */
public final class NetworkUtils {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private NetworkUtils() {}

    /**
     * Asenkron reverse DNS sorgusu.
     */
    public static CompletableFuture<String> reverseDNS(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InetAddress addr = InetAddress.getByName(ip);
                String hostname = addr.getCanonicalHostName();
                return hostname.equals(ip) ? "Bulunamadı" : hostname;
            } catch (Exception e) {
                return "Bulunamadı";
            }
        });
    }

    /**
     * Belirtilen port'un açık olup olmadığını kontrol eder.
     */
    public static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * IP'den hostname alır (senkron, timeout ile).
     */
    public static String getHostname(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            String hostname = addr.getCanonicalHostName();
            return hostname.equals(ip) ? ip : hostname;
        } catch (Exception e) {
            return ip;
        }
    }

    /**
     * Senkron HTTP GET isteği.
     */
    public static String httpGet(String url, int timeoutMs) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .header("User-Agent", "AtomGuard-Velocity/1.0.0")
                .build();
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Senkron HTTP GET isteği - ek başlık ile.
     */
    public static String httpGet(String url, int timeoutMs, String headerKey, String headerValue) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .header("User-Agent", "AtomGuard-Velocity/1.0.0")
                .header(headerKey, headerValue)
                .build();
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Asenkron HTTP GET isteği (Java HttpClient).
     */
    public static CompletableFuture<String> httpGetAsync(String url, int timeoutSeconds) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .GET()
            .header("User-Agent", "AtomGuard-Velocity/1.0.0")
            .build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body);
    }

    /**
     * Basit JSON alanı çıkarma (Gson gerektirmez, tek string alanları için).
     */
    public static String extractJsonField(String json, String field) {
        if (json == null || field == null) return null;
        String search = "\"" + field + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) start++;
        if (start >= json.length()) return null;
        char first = json.charAt(start);
        if (first == '"') {
            int end = json.indexOf('"', start + 1);
            if (end < 0) return null;
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') end++;
            return json.substring(start, end).trim();
        }
    }
}
