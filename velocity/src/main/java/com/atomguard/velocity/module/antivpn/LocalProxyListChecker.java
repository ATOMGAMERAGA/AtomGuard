package com.atomguard.velocity.module.antivpn;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Yerel IP listesi tabanlı VPN/proxy kontrolü.
 */
public class LocalProxyListChecker {

    private final Set<String> proxyIPs = ConcurrentHashMap.newKeySet();
    private final Path listFile;
    private final Logger logger;

    public LocalProxyListChecker(Path dataDirectory, Logger logger) {
        this.listFile = dataDirectory.resolve("proxy-list.txt");
        this.logger = logger;
    }

    public void load() {
        if (!Files.exists(listFile)) {
            logger.info("Yerel proxy listesi bulunamadı: {}", listFile);
            return;
        }
        try (Stream<String> lines = Files.lines(listFile, StandardCharsets.UTF_8)) {
            proxyIPs.clear();
            lines.map(String::trim)
                 .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                 .forEach(proxyIPs::add);
            logger.info("Yerel proxy listesi yüklendi: {} giriş", proxyIPs.size());
        } catch (IOException e) {
            logger.error("Proxy listesi yüklenemedi: {}", e.getMessage());
        }
    }

    public boolean isProxy(String ip) {
        return proxyIPs.contains(ip);
    }

    public void addIP(String ip) { proxyIPs.add(ip); }
    public void removeIP(String ip) { proxyIPs.remove(ip); }
    public int size() { return proxyIPs.size(); }
}
