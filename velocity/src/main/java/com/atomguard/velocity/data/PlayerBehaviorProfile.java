package com.atomguard.velocity.data;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerBehaviorProfile {
    private final String ip;
    private int totalSessions = 0;
    private int successfulLogins = 0;
    private int failedChecks = 0;
    private long firstSeen;
    private long lastSeen;
    // ConcurrentHashMap.newKeySet(): thread-safe Set — recordSession() birden fazla
    // Netty thread'inden çağrılabilir; HashSet race condition / CME riskini önler.
    private final Set<String> usedUsernames = ConcurrentHashMap.newKeySet();
    private String lastCountry;
    /**
     * Offline-mode sunucularda farklı kullanıcı adı penaltısını devre dışı bırakır.
     * Aynı IP'den birden fazla hesap normal bir davranıştır (aile, cracked).
     */
    private boolean offlineModeLenient = false;

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
    public void setOfflineModeLenient(boolean lenient) { this.offlineModeLenient = lenient; }
    public boolean isOfflineModeLenient() { return offlineModeLenient; }

    // Trust Score: 0-100 (davranışa dayalı güven)
    public int calculateTrustScore() {
        int score = 50; // Başlangıç (nötr)

        // İlk giriş bonusu: tamamen yeni bir oyuncuya 5 puan ek güven ver.
        // trust-score-threshold yanlışlıkla yüksek (ör. 55) ayarlanmış olsa bile
        // yeni oyuncuların kicklenmemesini garanti eder.
        if (totalSessions == 0 && successfulLogins == 0 && failedChecks == 0) {
            score += 5; // → 55: varsayılan eşik (5) içinde güvenle geçer
        }

        score += Math.min(25, successfulLogins * 3);  // Başarılı girişler güveni artırır
        // Violation cezası yumuşatıldı: 8→5, max 40→30
        // Rate limit ve throttle gibi geçici olaylar da violation olarak kaydediliyordu;
        // bunların etkisini azaltmak için ceza ve tavan düşürüldü.
        score -= Math.min(30, failedChecks * 5);

        if (totalSessions > 20) score += 15;           // Sadık kullanıcı bonusu
        // Offline-mode'da aynı IP'den farklı isimle giriş normaldir (aile, cracked launcher).
        // offlineModeLenient flag'i TrustScoreCheck'ten önce set edilmemiş olabilir;
        // bu durumda penaltı uygulanmamalı (false olduğunda sadece gerçekten online-mode demektir).
        if (!offlineModeLenient && usedUsernames.size() > 6) score -= 10; // eşik: 3→6, ceza: 20→10

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
