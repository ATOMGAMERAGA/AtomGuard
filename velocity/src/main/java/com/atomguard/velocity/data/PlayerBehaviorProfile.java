package com.atomguard.velocity.data;

import java.util.HashSet;
import java.util.Set;

public class PlayerBehaviorProfile {
    private final String ip;
    private int totalSessions = 0;
    private int successfulLogins = 0;
    private int failedChecks = 0;
    private long firstSeen;
    private long lastSeen;
    private final Set<String> usedUsernames = new HashSet<>();
    private String lastCountry;

    public PlayerBehaviorProfile(String ip) {
        this.ip = ip;
        this.firstSeen = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
    }

    public void recordSession(String username, String country) {
        this.totalSessions++;
        this.lastSeen = System.currentTimeMillis();
        this.usedUsernames.add(username);
        this.lastCountry = country;
    }

    public void recordLoginSuccess() { this.successfulLogins++; }
    public void recordViolation() { this.failedChecks++; }

    // Trust Score: 0-100 (davranışa dayalı güven)
    public int calculateTrustScore() {
        int score = 50; // Başlangıç (nötr)
        
        score += Math.min(25, successfulLogins * 3);  // Başarılı girişler güveni artırır
        score -= Math.min(40, failedChecks * 8);       // İhlaller güveni hızla düşürür
        
        if (totalSessions > 20) score += 15;           // Sadık kullanıcı bonusu
        if (usedUsernames.size() > 3) score -= 20;     // Sık isim değiştirme şüphelidir
        
        long ageDays = (System.currentTimeMillis() - firstSeen) / 86_400_000L;
        score += (int) Math.min(10, ageDays);          // Hesap yaşı bonusu

        return Math.max(0, Math.min(100, score));
    }

    // Getters/Setters for persistence
    public String getIp() { return ip; }
    public int getTotalSessions() { return totalSessions; }
    public int getSuccessfulLogins() { return successfulLogins; }
    public int getFailedChecks() { return failedChecks; }
    public long getFirstSeen() { return firstSeen; }
    public long getLastSeen() { return lastSeen; }
    public Set<String> getUsedUsernames() { return usedUsernames; }
    public String getLastCountry() { return lastCountry; }

    public void loadFromJson(org.json.JSONObject json) {
        if (json.has("sessions")) this.totalSessions = json.getInt("sessions");
        if (json.has("logins")) this.successfulLogins = json.getInt("logins");
        if (json.has("fails")) this.failedChecks = json.getInt("fails");
        if (json.has("first")) this.firstSeen = json.getLong("first");
        if (json.has("names")) {
            org.json.JSONArray names = json.getJSONArray("names");
            for (int i = 0; i < names.length(); i++) usedUsernames.add(names.getString(i));
        }
    }
}
