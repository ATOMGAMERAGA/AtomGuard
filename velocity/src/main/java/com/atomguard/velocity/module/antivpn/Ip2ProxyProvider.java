package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.AtomGuardVelocity;
import com.ip2proxy.IP2Proxy;

import java.nio.file.Path;

public class Ip2ProxyProvider {

    private final AtomGuardVelocity plugin;
    private IP2Proxy proxy;
    private boolean available = false;

    public Ip2ProxyProvider(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        String dbPath = plugin.getConfigManager().getString("vpn-proxy-engelleme.ip2proxy.veritabani-yolu", "IP2PROXY-LITE-PX2.BIN");
        Path path = plugin.getDataDirectory().resolve(dbPath);
        
        if (path.toFile().exists()) {
            try {
                proxy = new IP2Proxy();
                proxy.Open(path.toString(), IP2Proxy.IOModes.IP2PROXY_MEMORY_MAPPED);
                available = true;
                plugin.getSlf4jLogger().info("IP2Proxy database loaded.");
            } catch (Exception e) {
                plugin.getSlf4jLogger().error("Failed to load IP2Proxy database", e);
            }
        }
    }

    public boolean isProxy(String ip) {
        if (!available) return false;
        try {
            return proxy.IsProxy(ip) > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    public void close() {
        if (proxy != null) {
            proxy.Close();
        }
    }
}