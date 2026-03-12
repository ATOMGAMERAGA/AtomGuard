package com.atomguard.velocity.util;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class HttpClientUtil {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpClientUtil() {}

    public static CompletableFuture<String> getAsync(String url, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET();
        headers.forEach(builder::header);
        return CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    public static CompletableFuture<String> postAsync(String url, String body, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    public static String getSync(String url, Map<String, String> headers, Duration timeout) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET();
        headers.forEach(builder::header);
        HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + url);
        }
        return response.body();
    }

    /**
     * Downloads a URL to a file synchronously.
     */
    public static void downloadToFile(String url, Path target, Map<String, String> headers, Duration timeout) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET();
        headers.forEach(builder::header);
        HttpResponse<InputStream> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + url);
        }
        try (InputStream in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
