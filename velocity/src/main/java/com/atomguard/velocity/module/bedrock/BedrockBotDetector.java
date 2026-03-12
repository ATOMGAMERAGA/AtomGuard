package com.atomguard.velocity.module.bedrock;

import com.atomguard.velocity.AtomGuardVelocity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bedrock platformuna ozgu bot tespit mekanizmasi.
 *
 * <p>Bedrock botlari Java Edition botlarindan farkli davranis kaliplari gosterir:
 * farkli client brand, farkli paket zamanlama vb.</p>
 */
public class BedrockBotDetector {

    private final AtomGuardVelocity plugin;
    private final Map<String, AtomicInteger> ipConnectionCounts = new ConcurrentHashMap<>();

    public BedrockBotDetector(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    /**
     * Bedrock oyuncusunun bot olma ihtimalini degerlendirir.
     *
     * @param ip       Oyuncu IP adresi
     * @param username Oyuncu adi
     * @param brand    Client brand bilgisi
     * @return Bot skoru (0-100, yuksek = daha suphelı)
     */
    public int evaluateThreat(String ip, String username, String brand) {
        int score = 0;

        // Bedrock botlari genellikle bos veya standart olmayan brand gonderir
        if (brand == null || brand.isBlank()) {
            score += 30;
        } else if (!brand.contains("Geyser") && !brand.contains("Bedrock")) {
            score += 10;
        }

        // Ayni IP'den cok sayida Bedrock baglantisi
        AtomicInteger count = ipConnectionCounts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        int connections = count.incrementAndGet();
        if (connections > 5) {
            score += Math.min(connections * 5, 40);
        }

        // Kullanici adi kalip kontrolu
        if (username != null && username.matches("^\\.[A-Za-z]{3,}\\d{4,}$")) {
            score += 15; // Otomatik olusturulmus Bedrock isim patterni
        }

        return Math.min(score, 100);
    }

    /**
     * Periyodik temizlik — eski IP sayaclarini sifirlar.
     */
    public void cleanup() {
        ipConnectionCounts.clear();
    }
}
