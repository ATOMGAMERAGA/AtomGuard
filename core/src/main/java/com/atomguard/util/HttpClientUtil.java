package com.atomguard.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Senkron ve asenkron HTTP istekleri için yardımcı sınıf. @since 2.0.0 */
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
}
