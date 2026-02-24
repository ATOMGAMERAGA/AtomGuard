package com.atomguard.module.honeypot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Honeypot modülü için istatistik takibi.
 * Thread-safe sayaçlar ve port başına bağlantı dağılımını tutar.
 * Son MAX_RECENT bağlantıyı bellekte saklar.
 */
public class HoneypotStatistics {

    /** Honeypot portunu ilk kez ziyaret eden benzersiz IP sayısı */
    private final AtomicLong totalTrapped = new AtomicLong(0);

    /** Port numarası -> toplam bağlantı sayısı */
    private final ConcurrentHashMap<Integer, AtomicLong> portConnectionCounts = new ConcurrentHashMap<>();

    /** IP -> kaç kez yakalandığı */
    private final ConcurrentHashMap<String, AtomicLong> uniqueIpTrapped = new ConcurrentHashMap<>();

    /** Son MAX_RECENT bağlantıların deque'si (yeniden eskiye sıralı) */
    private final ConcurrentLinkedDeque<HoneypotConnection> recentConnections = new ConcurrentLinkedDeque<>();

    private static final int MAX_RECENT = 100;

    /**
     * Yeni bir honeypot bağlantısını istatistiklere kaydeder.
     *
     * @param conn Kaydedilecek bağlantı
     */
    public void record(HoneypotConnection conn) {
        // Port sayacı
        portConnectionCounts.computeIfAbsent(conn.getPort(), k -> new AtomicLong()).incrementAndGet();

        // Benzersiz IP sayacı — sadece ilk ziyarette toplam sayacı artır
        long ipCount = uniqueIpTrapped.computeIfAbsent(conn.getIp(), k -> new AtomicLong()).incrementAndGet();
        if (ipCount == 1) {
            totalTrapped.incrementAndGet();
        }

        // Recency deque — baş tarafa ekle, kuyruktan taşanları at
        recentConnections.addFirst(conn);
        while (recentConnections.size() > MAX_RECENT) {
            recentConnections.removeLast();
        }
    }

    /** Honeypot tarafından yakalanan toplam benzersiz IP sayısı */
    public long getTotalTrapped() { return totalTrapped.get(); }

    /** Port başına bağlantı sayılarının kopyası */
    public ConcurrentHashMap<Integer, AtomicLong> getPortCounts() { return portConnectionCounts; }

    /** Son bağlantıların anlık görüntüsü (en yeni önce) */
    public List<HoneypotConnection> getRecentConnections() {
        return new ArrayList<>(recentConnections);
    }

    /** Belirli bir IP'nin kaç kez yakalandığı */
    public long getIpTrapCount(String ip) {
        AtomicLong counter = uniqueIpTrapped.get(ip);
        return counter == null ? 0L : counter.get();
    }

    /** Şimdiye kadar bağlantı gören port sayısı */
    public int getActivePortCount() { return portConnectionCounts.size(); }
}
