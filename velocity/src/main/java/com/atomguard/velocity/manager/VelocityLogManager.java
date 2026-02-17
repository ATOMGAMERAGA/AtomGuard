package com.atomguard.velocity.manager;

import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class VelocityLogManager {

    private final Logger logger;
    private final Path logDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "atomguard-velocity-log");
        t.setDaemon(true);
        return t;
    });
    private final DateTimeFormatter timestampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public VelocityLogManager(Path dataDirectory, Logger logger) {
        this.logger = logger;
        this.logDir = dataDirectory.resolve("logs");
    }

    public void initialize() {
        try {
            Files.createDirectories(logDir);
            cleanOldLogs(7);
        } catch (IOException e) {
            logger.error("Log dizini oluşturulamadı: {}", e.getMessage());
        }
    }

    public void log(String message) { writeAsync("[INFO] " + message); }
    public void warn(String message) { writeAsync("[WARN] " + message); logger.warn(message); }
    public void error(String message) { writeAsync("[ERROR] " + message); logger.error(message); }

    private void writeAsync(String message) {
        executor.submit(() -> {
            try {
                Path logFile = logDir.resolve("velocity-" + LocalDate.now().format(dateFmt) + ".log");
                String line = LocalDateTime.now().format(timestampFmt) + " " + message + "\n";
                Files.writeString(logFile, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                logger.error("Log yazma hatası: {}", e.getMessage());
            }
        });
    }

    private void cleanOldLogs(int retentionDays) {
        try (Stream<Path> files = Files.list(logDir)) {
            LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
            files.filter(p -> p.getFileName().toString().startsWith("velocity-"))
                    .filter(p -> {
                        try {
                            String name = p.getFileName().toString().replace("velocity-", "").replace(".log", "");
                            return LocalDate.parse(name, dateFmt).isBefore(cutoff);
                        } catch (Exception e) { return false; }
                    })
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        } catch (IOException e) {
            logger.warn("Eski log temizleme hatası: {}", e.getMessage());
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
