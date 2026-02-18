package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ExternalListFetcher {
    
    private final AtomGuardVelocity plugin;
    
    public ExternalListFetcher(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<List<String>> fetch(String urlString) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> lines = new ArrayList<>();
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "AtomGuard/1.0.0");
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
            } catch (Exception e) {
                plugin.getSlf4jLogger().warn("Failed to fetch external list {}: {}", urlString, e.getMessage());
            }
            return lines;
        });
    }
}