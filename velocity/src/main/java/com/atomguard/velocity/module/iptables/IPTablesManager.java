package com.atomguard.velocity.module.iptables;

import com.atomguard.velocity.AtomGuardVelocity;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IPTablesManager {

    private final AtomGuardVelocity plugin;
    private final Logger logger;
    private final String iptablesPath;
    private final String ip6tablesPath;
    private final String nftablesPath;
    private final boolean useNftables;
    private boolean available = false;
    private final ExecutorService executor;

    public IPTablesManager(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSlf4jLogger();
        this.executor = Executors.newSingleThreadExecutor();
        
        this.iptablesPath = plugin.getConfigManager().getString("iptables-entegrasyon.iptables-yolu", "/usr/sbin/iptables");
        this.ip6tablesPath = plugin.getConfigManager().getString("iptables-entegrasyon.ip6tables-yolu", "/usr/sbin/ip6tables");
        this.nftablesPath = plugin.getConfigManager().getString("iptables-entegrasyon.nftables-yolu", "/usr/sbin/nft");
        this.useNftables = plugin.getConfigManager().getBoolean("iptables-entegrasyon.nftables-mod", false);

        checkAvailability();
    }

    private void checkAvailability() {
        CompletableFuture.runAsync(() -> {
            try {
                String cmd = useNftables ? nftablesPath : iptablesPath;
                ProcessBuilder pb = new ProcessBuilder("sudo", "-n", cmd, useNftables ? "-v" : "-L", "-n");
                Process process = pb.start();
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    this.available = true;
                    logger.info("IPTables/NFTables entegrasyonu aktif. (Mod: {})", useNftables ? "NFTables" : "Legacy");
                    if (plugin.getConfigManager().getBoolean("iptables-entegrasyon.kural-temizleme.baslangic-temizle", true)) {
                        flushAtomGuardRules();
                    }
                } else {
                    logger.warn("IPTables entegrasyonu kullanılamıyor (Exit: {}). Root yetkisi veya paket eksik olabilir.", exitCode);
                    this.available = false;
                }
            } catch (Exception e) {
                logger.error("IPTables kontrolü başarısız: {}", e.getMessage());
                this.available = false;
            }
        }, executor);
    }

    public boolean isAvailable() {
        return available;
    }

    public CompletableFuture<Boolean> banIP(String ip, long durationMs) {
        if (!available) return CompletableFuture.completedFuture(false);

        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean isIPv6 = ip.contains(":");
                String cmd = isIPv6 ? ip6tablesPath : iptablesPath;
                
                ProcessBuilder pb;
                if (useNftables) {
                    // NFTables syntax (basic example, assumes table 'filter' and chain 'input' exist)
                    pb = new ProcessBuilder("sudo", nftablesPath, "add", "rule", "ip", "filter", "input", "ip", "saddr", ip, "drop", "comment", "\"AtomGuard-Ban\"");
                } else {
                    // Legacy IPTables
                    pb = new ProcessBuilder("sudo", cmd, "-I", "INPUT", "-s", ip, "-j", "DROP", "-m", "comment", "--comment", "AtomGuard-Ban");
                }
                
                return executeCommand(pb, "Ban IP " + ip);
            } catch (Exception e) {
                logger.error("IP banlama hatası ({}): {}", ip, e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> unbanIP(String ip) {
        if (!available) return CompletableFuture.completedFuture(false);

        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean isIPv6 = ip.contains(":");
                String cmd = isIPv6 ? ip6tablesPath : iptablesPath;

                ProcessBuilder pb;
                if (useNftables) {
                     // NFTables deletion is complex without handle, skipping for simplicity in prototype
                     return false; 
                } else {
                    // Legacy IPTables -D
                    pb = new ProcessBuilder("sudo", cmd, "-D", "INPUT", "-s", ip, "-j", "DROP", "-m", "comment", "--comment", "AtomGuard-Ban");
                }

                boolean success = executeCommand(pb, "Unban IP " + ip);
                if (!success && !useNftables) {
                    // Try without comment
                    ProcessBuilder pb2 = new ProcessBuilder("sudo", cmd, "-D", "INPUT", "-s", ip, "-j", "DROP");
                    return executeCommand(pb2, "Unban IP (Fallback) " + ip);
                }
                return success;
            } catch (Exception e) {
                logger.error("IP ban kaldırma hatası ({}): {}", ip, e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> banSubnet(String subnet) {
        if (!available) return CompletableFuture.completedFuture(false);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String cmd = subnet.contains(":") ? ip6tablesPath : iptablesPath;
                ProcessBuilder pb = new ProcessBuilder("sudo", cmd, "-I", "INPUT", "-s", subnet, "-j", "DROP", "-m", "comment", "--comment", "AtomGuard-SubnetBan");
                return executeCommand(pb, "Ban Subnet " + subnet);
            } catch (Exception e) {
                logger.error("Subnet banlama hatası ({}): {}", subnet, e.getMessage());
                return false;
            }
        }, executor);
    }

    public void flushAtomGuardRules() {
        if (!available || useNftables) return; // NFTables flush not implemented yet

        CompletableFuture.runAsync(() -> {
            try {
                // List rules with line numbers, grep AtomGuard, sort reverse to delete from bottom up
                // This is a simplified approach. A safer way is recommended for production.
                // For now, we rely on the implementation to just log this action.
                // Implementing a safe flush via Java ProcessBuilder is complex and risky without a proper library.
                // We will implement a "best effort" using a shell script approach if needed, but for now:
                
                // Strategy: 
                // iptables -S INPUT | grep "AtomGuard" | sed 's/-A/-D/' > /tmp/ag_cleanup
                // while read line; do sudo iptables $line; done < /tmp/ag_cleanup
                
                logger.info("AtomGuard kuralları temizleniyor...");
                String[] cleanupCmd = {
                    "/bin/sh", "-c",
                    "sudo " + iptablesPath + " -S INPUT | grep 'AtomGuard' | sed 's/-A/-D/' | while read rule; do sudo " + iptablesPath + " $rule; done"
                };
                ProcessBuilder pb = new ProcessBuilder(cleanupCmd);
                executeCommand(pb, "Flush Rules");
                
            } catch (Exception e) {
                logger.error("Kural temizleme hatası: {}", e.getMessage());
            }
        }, executor);
    }

    private boolean executeCommand(ProcessBuilder pb, String actionName) {
        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                if (plugin.getConfigManager().getBoolean("iptables-entegrasyon.loglama.komut-logla", true)) {
                    logger.info("IPTables: {} başarılı.", actionName);
                }
                return true;
            } else {
                if (plugin.getConfigManager().getBoolean("iptables-entegrasyon.loglama.hata-logla", true)) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String error = reader.readLine();
                        logger.error("IPTables hatası ({}): {}", actionName, error != null ? error : "Bilinmiyor");
                    }
                }
                return false;
            }
        } catch (Exception e) {
            logger.error("Komut yürütme hatası: {}", e.getMessage());
            return false;
        }
    }
    
    public void shutdown() {
        executor.shutdownNow();
    }
}