package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AccountFirewallModule extends VelocityModule {

    private final AccountBlacklist blacklist;
    private final ConcurrentHashMap<String, Long> accountAgeCache = new ConcurrentHashMap<>();
    private final HttpClient httpClient;

    public AccountFirewallModule(AtomGuardVelocity plugin) {
        super(plugin, "hesap-guvenlik-duvari");
        this.blacklist = new AccountBlacklist(plugin);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public int getPriority() { return 80; }

    @Override
    public java.util.List<String> getDependencies() {
        return java.util.List.of("guvenlik-duvari");
    }

    @Override
    public void onEnable() {
        plugin.getProxyServer().getEventManager().register(plugin, this);
        logger.info("Account Firewall module enabled.");
    }

    @Override
    public void onDisable() {
        plugin.getProxyServer().getEventManager().unregisterListener(plugin, this);
        logger.info("Account Firewall module disabled.");
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (!isEnabled()) return;

        String username = event.getUsername();
        
        if (blacklist.isBlacklisted(username)) {
             String reason = plugin.getMessageManager().getRaw("hesap-guvenlik-duvari.hesap-yasakli")
                     .replace("{sebep}", "Kara liste");
             event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                     plugin.getMessageManager().parse(reason)
             ));
        }
    }

    // Called from Listener manually if needed, or we can use the PreLoginEvent logic here if we hook it in Listener
    public CompletableFuture<AccountFirewallResult> checkAsync(String username, UUID uuid, boolean isPremium) {
        if (!isEnabled()) return CompletableFuture.completedFuture(AccountFirewallResult.allow());

        // 1. Blacklist Check
        if (uuid != null && blacklist.isBlacklisted(uuid)) {
            return CompletableFuture.completedFuture(AccountFirewallResult.deny("UUID Yasaklı"));
        }
        if (blacklist.isBlacklisted(username)) {
            return CompletableFuture.completedFuture(AccountFirewallResult.deny("Kullanıcı Adı Yasaklı"));
        }

        // 2. Cracked Policy
        String crackedPolicy = getConfigString("cracked-hesap.politika", "izin-ver");
        if (!isPremium && "engelle".equalsIgnoreCase(crackedPolicy)) {
             return CompletableFuture.completedFuture(AccountFirewallResult.deny(
                     plugin.getMessageManager().getRaw("hesap-guvenlik-duvari.cracked-engel")
             ));
        }

        // 3. Account Age Check (Async)
        if (isPremium && getConfigBoolean("hesap-yas-kontrolu.aktif", false)) {
            return fetchAccountCreationDate(username).thenApply(createdAt -> {
                if (createdAt == -1) return AccountFirewallResult.allow(); // Unknown age, allow
                
                long ageDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - createdAt);
                long minDays = getConfigLong("hesap-yas-kontrolu.minimum-gun", 7);
                
                if (ageDays < minDays) {
                    return AccountFirewallResult.deny(
                            plugin.getMessageManager().getRaw("hesap-guvenlik-duvari.hesap-cok-yeni")
                                    .replace("{gun}", String.valueOf(minDays))
                    );
                }
                return AccountFirewallResult.allow();
            });
        }

        return CompletableFuture.completedFuture(AccountFirewallResult.allow());
    }

    private CompletableFuture<Long> fetchAccountCreationDate(String username) {
        if (accountAgeCache.containsKey(username)) {
            return CompletableFuture.completedFuture(accountAgeCache.get(username));
        }

        // Use Ashcon API for creation date
        String url = "https://api.ashcon.app/mojang/v2/user/" + username;
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("created_at") && !json.get("created_at").isJsonNull()) {
                                String dateStr = json.get("created_at").getAsString();
                                // Parse ISO 8601 date
                                long time = Instant.parse(dateStr).toEpochMilli();
                                accountAgeCache.put(username, time);
                                return time;
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to parse Ashcon API response for {}: {}", username, e.getMessage());
                        }
                    }
                    return -1L;
                })
                .exceptionally(e -> {
                    logger.warn("Failed to fetch account age for {}: {}", username, e.getMessage());
                    return -1L;
                });
    }

    public void banAccount(UUID uuid, String username, String reason) {
        if (uuid != null) blacklist.addUuid(uuid);
        if (username != null) blacklist.addName(username);
        logger.info("Account banned: {} ({}) - Reason: {}", username, uuid, reason);
    }
    
    public void unbanAccount(UUID uuid) {
        if (uuid != null) blacklist.removeUuid(uuid);
        logger.info("Account unbanned: {}", uuid);
    }
}
