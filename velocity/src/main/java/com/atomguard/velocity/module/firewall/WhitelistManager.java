package com.atomguard.velocity.module.firewall;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * IP beyaz liste yönetimi.
 */
public class WhitelistManager {

    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    private final Path whitelistFile;
    private final Logger logger;

    public WhitelistManager(Path dataDirectory, Logger logger) {
        this.whitelistFile = dataDirectory.resolve("whitelist.txt");
        this.logger = logger;
    }

    public void load() {
        if (!Files.exists(whitelistFile)) return;
        try (Stream<String> lines = Files.lines(whitelistFile, StandardCharsets.UTF_8)) {
            whitelist.clear();
            lines.map(String::trim)
                 .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                 .forEach(whitelist::add);
            logger.info("Beyaz liste yüklendi: {} IP", whitelist.size());
        } catch (IOException e) {
            logger.error("Beyaz liste yüklenemedi: {}", e.getMessage());
        }
    }

    public void add(String ip) {
        whitelist.add(ip);
        appendToFile(ip);
    }

    public void remove(String ip) {
        whitelist.remove(ip);
        saveAll();
    }

    public boolean isWhitelisted(String ip) { return whitelist.contains(ip); }
    public int size() { return whitelist.size(); }
    public Set<String> getAll() { return whitelist; }

    private void appendToFile(String ip) {
        try {
            Files.writeString(whitelistFile, ip + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.warn("Beyaz listeye yazılamadı: {}", e.getMessage());
        }
    }

    private void saveAll() {
        try {
            Files.writeString(whitelistFile,
                String.join("\n", whitelist) + "\n",
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Beyaz liste kaydedilemedi: {}", e.getMessage());
        }
    }
}
