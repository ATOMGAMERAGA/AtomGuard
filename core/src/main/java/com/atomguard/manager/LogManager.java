package com.atomguard.manager;

import com.atomguard.AtomGuard;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * Asenkron log sistemi manager sınıfı
 * Performanslı ve thread-safe log yönetimi sağlar
 * Günlük dosyalar oluşturur ve eski logları temizler
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class LogManager {

    private final AtomGuard plugin;
    private final ExecutorService logExecutor;
    private final BlockingQueue<LogEntry> logQueue;

    private File logFolder;
    private File currentLogFile;
    private BufferedWriter logWriter;

    private volatile boolean running;
    private Future<?> logTask;

    // Date formatter'lar
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * LogManager constructor
     *
     * @param plugin Ana plugin instance
     */
    public LogManager(@NotNull AtomGuard plugin) {
        this.plugin = plugin;
        this.logExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "AtomGuard-Logger");
            thread.setDaemon(true);
            return thread;
        });
        this.logQueue = new LinkedBlockingQueue<>();
        this.running = false;
    }

    /**
     * Log sistemini başlatır
     */
    public void start() {
        if (running) {
            return;
        }

        // Log aktif değilse başlatma
        if (!plugin.getConfigManager().isLogEnabled()) {
            plugin.getLogger().info("Log sistemi devre dışı.");
            return;
        }

        // Log klasörünü oluştur
        String logFolderPath = plugin.getConfigManager().getLogFolder();
        logFolder = new File(plugin.getDataFolder().getParentFile().getParentFile(), logFolderPath);

        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }

        // Günlük log dosyası oluştur
        if (plugin.getConfigManager().isDailyLogEnabled()) {
            createDailyLogFile();
        }

        // Async log task'ı başlat
        running = true;
        logTask = logExecutor.submit(this::processLogs);

        // Eski logları temizle
        cleanOldLogs();

        plugin.getLogger().info("Log sistemi başlatıldı: " + logFolder.getAbsolutePath());
    }

    /**
     * Log sistemini durdurur
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        // Kalan logları işle
        try {
            // Queue'daki tüm logları işle
            while (!logQueue.isEmpty()) {
                LogEntry entry = logQueue.poll();
                if (entry != null) {
                    writeLogEntry(entry);
                }
            }

            // Task'ı iptal et
            if (logTask != null && !logTask.isDone()) {
                logTask.cancel(false);
            }

            // Writer'ı kapat
            closeWriter();

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Log sistemi durdurulurken hata!", e);
        } finally {
            logExecutor.shutdown();
            try {
                if (!logExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        plugin.getLogger().info("Log sistemi durduruldu.");
    }

    /**
     * Async log işleme döngüsü
     */
    private void processLogs() {
        while (running) {
            try {
                // Queue'den log al (1 saniye timeout)
                LogEntry entry = logQueue.poll(1, TimeUnit.SECONDS);
                if (entry != null) {
                    writeLogEntry(entry);
                }

                // Günlük dosya kontrolü
                if (plugin.getConfigManager().isDailyLogEnabled()) {
                    checkDailyLogRotation();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Log işlenirken hata!", e);
            }
        }
    }

    /**
     * Günlük log dosyası oluşturur
     */
    private void createDailyLogFile() {
        try {
            closeWriter();

            String fileName = "atomguard-" + LocalDate.now().format(FILE_DATE_FORMAT) + ".log";
            currentLogFile = new File(logFolder, fileName);

            if (!currentLogFile.exists()) {
                currentLogFile.createNewFile();
            }

            logWriter = new BufferedWriter(new FileWriter(currentLogFile, true));

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Log dosyası oluşturulamadı!", e);
        }
    }

    /**
     * Günlük dosya rotasyonunu kontrol eder
     */
    private void checkDailyLogRotation() {
        if (currentLogFile == null) {
            return;
        }

        String currentDate = LocalDate.now().format(FILE_DATE_FORMAT);
        if (!currentLogFile.getName().contains(currentDate)) {
            createDailyLogFile();
        }
    }

    /**
     * Writer'ı kapatır
     */
    private void closeWriter() {
        if (logWriter != null) {
            try {
                logWriter.flush();
                logWriter.close();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Log writer kapatılamadı!", e);
            }
            logWriter = null;
        }
    }

    /**
     * Log entry'sini dosyaya yazar
     *
     * @param entry Log entry
     */
    private void writeLogEntry(@NotNull LogEntry entry) {
        if (logWriter == null) {
            return;
        }

        try {
            String logLine = String.format("[%s] [%s] %s%n",
                entry.timestamp.format(LOG_TIME_FORMAT),
                entry.level,
                entry.message
            );

            logWriter.write(logLine);
            logWriter.flush();

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Log yazılamadı!", e);
        }
    }

    /**
     * Eski log dosyalarını temizler
     */
    private void cleanOldLogs() {
        int retentionDays = plugin.getConfigManager().getLogRetentionDays();
        if (retentionDays <= 0) {
            return; // 0 ise hiçbir zaman silme
        }

        logExecutor.submit(() -> {
            try {
                LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);

                File[] logFiles = logFolder.listFiles((dir, name) ->
                    name.startsWith("atomguard-") && name.endsWith(".log")
                );

                if (logFiles == null) {
                    return;
                }

                int deletedCount = 0;
                for (File file : logFiles) {
                    try {
                        String fileName = file.getName();
                        String dateStr = fileName.replace("atomguard-", "").replace(".log", "");
                        LocalDate fileDate = LocalDate.parse(dateStr, FILE_DATE_FORMAT);

                        if (fileDate.isBefore(cutoffDate)) {
                            if (file.delete()) {
                                deletedCount++;
                            }
                        }
                    } catch (Exception e) {
                        // Dosya adı parse edilemezse atla
                    }
                }

                if (deletedCount > 0) {
                    plugin.getLogger().info(deletedCount + " eski log dosyası temizlendi.");
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Eski loglar temizlenirken hata!", e);
            }
        });
    }

    /**
     * Log mesajı ekler (async)
     *
     * @param level Log seviyesi
     * @param message Log mesajı
     */
    public void log(@NotNull LogLevel level, @NotNull String message) {
        if (!running || !plugin.getConfigManager().isLogEnabled()) {
            return;
        }

        LogEntry entry = new LogEntry(LocalDateTime.now(), level, message);
        logQueue.offer(entry);

        // Debug modunda console'a da yaz
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[" + level + "] " + message);
        }
    }

    /**
     * Info seviyesinde log
     *
     * @param message Log mesajı
     */
    public void info(@NotNull String message) {
        log(LogLevel.INFO, message);
    }

    /**
     * Warning seviyesinde log
     *
     * @param message Log mesajı
     */
    public void warning(@NotNull String message) {
        log(LogLevel.WARNING, message);
    }

    /**
     * Error seviyesinde log
     *
     * @param message Log mesajı
     */
    public void error(@NotNull String message) {
        log(LogLevel.ERROR, message);
    }

    /**
     * Debug seviyesinde log
     *
     * @param message Log mesajı
     */
    public void debug(@NotNull String message) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            log(LogLevel.DEBUG, message);
        }
    }

    /**
     * Exploit engelleme logu
     *
     * @param playerName Oyuncu adı
     * @param exploitType Exploit türü
     * @param details Detaylar
     */
    public void logExploit(@NotNull String playerName, @NotNull String exploitType, @NotNull String details) {
        String message = String.format("EXPLOIT: %s | Oyuncu: %s | Detay: %s",
            exploitType, playerName, details);
        log(LogLevel.WARNING, message);
    }

    /**
     * Bot saldırısı logu
     *
     * @param playerName Oyuncu adı
     * @param ip IP adresi
     * @param reason Sebep
     */
    public void logBot(@NotNull String playerName, @NotNull String ip, @NotNull String reason) {
        String message = String.format("BOT: %s | IP: %s | Sebep: %s",
            playerName, ip, reason);
        log(LogLevel.WARNING, message);
    }

    /**
     * Log entry sınıfı
     */
    private static class LogEntry {
        final LocalDateTime timestamp;
        final LogLevel level;
        final String message;

        LogEntry(LocalDateTime timestamp, LogLevel level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }
    }

    /**
     * Log seviyeleri
     */
    public enum LogLevel {
        INFO,
        WARNING,
        ERROR,
        DEBUG
    }
}
