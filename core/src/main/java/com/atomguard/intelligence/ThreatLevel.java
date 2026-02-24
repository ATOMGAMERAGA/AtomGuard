package com.atomguard.intelligence;

/**
 * Tehdit seviyeleri.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public enum ThreatLevel {
    NORMAL("Normal", "<green>", 0x00FF00),
    ELEVATED("Yükselen", "<yellow>", 0xFFFF00),
    HIGH("Yüksek", "<gold>", 0xFFA500),
    CRITICAL("Kritik", "<red>", 0xFF0000);

    private final String displayName;
    private final String color;
    private final int discordColor;

    ThreatLevel(String displayName, String color, int discordColor) {
        this.displayName = displayName;
        this.color = color;
        this.discordColor = discordColor;
    }

    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
    public int getDiscordColor() { return discordColor; }
}
