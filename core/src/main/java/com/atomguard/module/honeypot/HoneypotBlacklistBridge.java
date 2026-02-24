package com.atomguard.module.honeypot;

import com.atomguard.AtomGuard;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Honeypot tuzaklarını ana kara liste sistemine köprüler.
 *
 * <p>Yakalanan IP'ler bu sınıf üzerinden hem yerel bellekte hem de
 * yapılandırılmış depolama sağlayıcısında saklanır. Redis etkinse
 * HONEYPOT_BLOCK mesajını cluster genelinde yayar.</p>
 */
public class HoneypotBlacklistBridge {

    private final AtomGuard plugin;

    /**
     * IP adresi -> son kullanma zaman damgası (epoch ms).
     * 0 değeri kalıcı engeli ifade eder.
     */
    private final ConcurrentHashMap<String, Long> honeypotBlacklist = new ConcurrentHashMap<>();

    public HoneypotBlacklistBridge(AtomGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Bir IP'yi honeypot kara listesine ekler.
     *
     * @param ip              Engellenecek IP adresi
     * @param durationSeconds Engel süresi saniye cinsinden; 0 veya negatif kalıcı engel anlamına gelir
     */
    public void blacklist(String ip, int durationSeconds) {
        long expiry = durationSeconds > 0
                ? System.currentTimeMillis() + (durationSeconds * 1000L)
                : 0L;
        honeypotBlacklist.put(ip, expiry);

        // Depolama sağlayıcısına kalıcı olarak yaz
        if (plugin.getStorageProvider() != null) {
            plugin.getStorageProvider().saveBlockedIP(ip, "honeypot-trap", expiry);
        }

        // Redis cluster senkronizasyonu
        if (plugin.getRedisManager() != null) {
            plugin.getRedisManager().publish("HONEYPOT_BLOCK", ip + ":" + expiry);
        }
    }

    /**
     * Belirtilen IP'nin kara listede olup olmadığını kontrol eder.
     * Süresi dolmuş kayıtlar otomatik olarak temizlenir.
     *
     * @param ip Kontrol edilecek IP adresi
     * @return Hâlâ geçerli bir engellenme varsa {@code true}
     */
    public boolean isBlacklisted(String ip) {
        Long expiry = honeypotBlacklist.get(ip);
        if (expiry == null) return false;
        if (expiry == 0L) return true; // kalıcı

        if (System.currentTimeMillis() > expiry) {
            honeypotBlacklist.remove(ip);
            return false;
        }
        return true;
    }

    /**
     * IP'yi kara listeden kaldırır.
     *
     * @param ip Kaldırılacak IP adresi
     */
    public void unblacklist(String ip) {
        honeypotBlacklist.remove(ip);
        if (plugin.getStorageProvider() != null) {
            plugin.getStorageProvider().removeBlockedIP(ip);
        }
    }

    /**
     * Süresi dolmuş tüm kara liste kayıtlarını temizler.
     * Periyodik bakım görevi tarafından çağrılmalıdır.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        honeypotBlacklist.entrySet().removeIf(e -> e.getValue() > 0L && now > e.getValue());
    }

    /** Mevcut kara liste boyutu (kalıcı + geçici) */
    public int getBlacklistSize() { return honeypotBlacklist.size(); }
}
