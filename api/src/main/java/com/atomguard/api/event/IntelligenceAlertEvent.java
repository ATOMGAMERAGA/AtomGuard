package com.atomguard.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Tehdit istihbaratı uyarı eventi.
 * Z-score eşiğini aşan anormal trafik kalıpları tespit edildiğinde fire edilir.
 */
public class IntelligenceAlertEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String threatLevel; // ELEVATED, HIGH, CRITICAL
    private final String details;
    private final double zScore;

    public IntelligenceAlertEvent(String threatLevel, String details, double zScore) {
        super(true); // async
        this.threatLevel = threatLevel;
        this.details = details;
        this.zScore = zScore;
    }

    /** Tehdit seviyesi: ELEVATED, HIGH veya CRITICAL */
    public String getThreatLevel() { return threatLevel; }

    /** Tehdit hakkında açıklayıcı mesaj */
    public String getDetails() { return details; }

    /** Hesaplanan Z-score değeri */
    public double getZScore() { return zScore; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
