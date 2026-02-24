package com.atomguard.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Saldırı snapshot'ı tamamlandı eventi.
 * Bir saldırı periyodu sona erdiğinde istatistiksel özet ile fire edilir.
 */
public class AttackSnapshotCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String snapshotId;
    private final String severity;
    private final String classification;
    private final long durationSeconds;
    private final long totalBlocked;

    public AttackSnapshotCompleteEvent(String snapshotId, String severity, String classification,
                                       long durationSeconds, long totalBlocked) {
        super(true); // async
        this.snapshotId = snapshotId;
        this.severity = severity;
        this.classification = classification;
        this.durationSeconds = durationSeconds;
        this.totalBlocked = totalBlocked;
    }

    /** Snapshot benzersiz kimliği */
    public String getSnapshotId() { return snapshotId; }

    /** Saldırı şiddeti: LOW, MEDIUM, HIGH, CRITICAL */
    public String getSeverity() { return severity; }

    /** Saldırı sınıflandırması: BOT_FLOOD, PORT_SCAN, EXPLOIT_PROBE vb. */
    public String getClassification() { return classification; }

    /** Saldırının sürdüğü saniye sayısı */
    public long getDurationSeconds() { return durationSeconds; }

    /** Toplam engellenen bağlantı/paket sayısı */
    public long getTotalBlocked() { return totalBlocked; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
