package com.atomguard.velocity.module.firewall;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Kalıcı IP kara liste yönetimi.
 */
public class BlacklistManager {

    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    private final Path blacklistFile;
    private final Logger logger;

    public BlacklistManager(Path dataDirectory, Logger logger) {
        this.blacklistFile = dataDirectory.resolve("blacklist.txt");
        this.logger = logger;
    }

    public void load() {
        if (!Files.exists(blacklistFile)) return;
        try (Stream<String> lines = Files.lines(blacklistFile, StandardCharsets.UTF_8)) {
            blacklist.clear();
            lines.map(String::trim)
                 .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                 .forEach(blacklist::add);
            logger.info("Kara liste yüklendi: {} IP", blacklist.size());
        } catch (IOException e) {
            logger.error("Kara liste yüklenemedi: {}", e.getMessage());
        }
    }

    public void add(String ip) {
        blacklist.add(ip);
        appendToFile(ip);
    }

    public void remove(String ip) {
        blacklist.remove(ip);
        saveAll();
    }

    public boolean isBlacklisted(String ip) { return blacklist.contains(ip); }
    public int size() { return blacklist.size(); }
    public Set<String> getAll() { return blacklist; }

    private void appendToFile(String ip) {
        try {
            Files.writeString(blacklistFile, ip + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.warn("Kara listeye yazılamadı: {}", e.getMessage());
        }
    }

    private void saveAll() {
        try {
            Files.writeString(blacklistFile,
                String.join("\n", blacklist) + "\n",
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Kara liste kaydedilemedi: {}", e.getMessage());
        }
    }
}
