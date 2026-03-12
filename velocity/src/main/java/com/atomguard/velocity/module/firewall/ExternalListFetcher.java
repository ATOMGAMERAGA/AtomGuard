package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.util.HttpClientUtil;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Harici liste indirici — uzak URL'lerden IP/CIDR listelerini asenkron olarak indirir.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class ExternalListFetcher {

    private final AtomGuardVelocity plugin;

    public ExternalListFetcher(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<List<String>> fetch(String urlString) {
        return HttpClientUtil.getAsync(urlString,
                Map.of("User-Agent", "AtomGuard/2.0.0"),
                Duration.ofSeconds(10))
            .thenApply(body -> Arrays.asList(body.split("\n")))
            .exceptionally(ex -> {
                plugin.getSlf4jLogger().warn("Failed to fetch external list {}: {}", urlString, ex.getMessage());
                return List.of();
            });
    }
}