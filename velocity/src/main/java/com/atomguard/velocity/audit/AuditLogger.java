package com.atomguard.velocity.audit;

import com.atomguard.velocity.storage.VelocityStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class AuditLogger {

    public enum EventType {
        CONNECTION_BLOCKED, BOT_DETECTED, VPN_DETECTED, IP_BANNED, IP_UNBANNED,
        ATTACK_STARTED, ATTACK_ENDED, MODULE_TOGGLED, CONFIG_RELOADED,
        PLAYER_VERIFIED, RATE_LIMITED, EXPLOIT_BLOCKED, COUNTRY_BLOCKED
    }

    public enum Severity { DEBUG, INFO, WARN, CRITICAL }

    private final VelocityStorageProvider storage;
    private final Logger logger = LoggerFactory.getLogger("AtomGuard-Audit");
    private final BlockingQueue<AuditEntry> writeQueue = new LinkedBlockingQueue<>(10000);
    private final Thread writerThread;
    private volatile boolean running = true;

    public AuditLogger(VelocityStorageProvider storage) {
        this.storage = storage;

        writerThread = new Thread(() -> {
            List<AuditEntry> batch = new ArrayList<>(50);
            while (running || !writeQueue.isEmpty()) {
                try {
                    AuditEntry entry = writeQueue.poll(5, TimeUnit.SECONDS);
                    if (entry != null) {
                        batch.add(entry);
                        writeQueue.drainTo(batch, 49);
                        processBatch(batch);
                        batch.clear();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "atomguard-audit-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void processBatch(List<AuditEntry> batch) {
        storage.batchInsertAudit(batch);
    }

    public void log(EventType type, String ip, String username,
                    String module, String detail, Severity severity) {
        if (!running) return;
        AuditEntry entry = new AuditEntry(
            System.currentTimeMillis(), type, ip, username, module, detail, severity);
        if (!writeQueue.offer(entry)) {
            logger.warn("Audit log kuyruğu dolu, giriş atlandı: {}", type);
        }
    }

    public void connectionBlocked(String ip, String module, String reason) {
        log(EventType.CONNECTION_BLOCKED, ip, null, module, reason, Severity.INFO);
    }

    public void shutdown() {
        running = false;
        try {
            writerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record AuditEntry(long timestamp, EventType type, String ip, String username,
                              String module, String detail, Severity severity) {}
}
