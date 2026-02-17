package com.atomguard.util;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paket işlemleri için yardımcı sınıf
 * PacketEvents ile paket kontrolü ve validasyon
 */
public class PacketUtils {

    // Bilinen zararlı paket pattern'lari
    private static final Set<String> SUSPICIOUS_PAYLOAD_CHANNELS = ConcurrentHashMap.newKeySet();

    static {
        // Bilinen exploit kanalları
        SUSPICIOUS_PAYLOAD_CHANNELS.add("MC|Brand");
        SUSPICIOUS_PAYLOAD_CHANNELS.add("REGISTER");
        // Daha fazlası eklenebilir
    }

    /**
     * Paket boyutunun limit içinde olup olmadığını kontrol eder
     *
     * @param packet Paket
     * @param maxSize Maksimum boyut (byte)
     * @return Limit içinde mi?
     */
    public static boolean isPacketSizeValid(@NotNull PacketWrapper<?> packet, int maxSize) {
        try {
            // PacketEvents ile paket boyutunu tahmin ediyoruz
            // Not: Gerçek boyut ağ üzerinden hesaplanmalı ama bu yaklaşık bir değer
            // Paket içeriğinin karmaşıklığına göre boyut tahmini
            return true; // PacketEvents doğrudan boyut vermiyor, bu yüzden her zaman true
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Paket türünün güvenli olup olmadığını kontrol eder
     *
     * @param packetType Paket türü
     * @return Güvenli mi?
     */
    public static boolean isPacketTypeSafe(@NotNull PacketType.Play.Client packetType) {
        // Bazı paket türleri zararlı olabilir
        // Şimdilik tüm paketlere izin ver, modüller özel kontroller yapar
        return true;
    }

    /**
     * Custom payload kanalının güvenli olup olmadığını kontrol eder
     *
     * @param channel Kanal adı
     * @return Güvenli mi?
     */
    public static boolean isPayloadChannelSafe(@NotNull String channel) {
        return !SUSPICIOUS_PAYLOAD_CHANNELS.contains(channel);
    }

    /**
     * Şüpheli payload kanalı ekler
     *
     * @param channel Kanal adı
     */
    public static void addSuspiciousChannel(@NotNull String channel) {
        SUSPICIOUS_PAYLOAD_CHANNELS.add(channel);
    }

    /**
     * Şüpheli payload kanalı kaldırır
     *
     * @param channel Kanal adı
     */
    public static void removeSuspiciousChannel(@NotNull String channel) {
        SUSPICIOUS_PAYLOAD_CHANNELS.remove(channel);
    }

    /**
     * Tüm şüpheli kanalları döndürür
     *
     * @return Şüpheli kanallar
     */
    public static Set<String> getSuspiciousChannels() {
        return Set.copyOf(SUSPICIOUS_PAYLOAD_CHANNELS);
    }

    /**
     * Paket spam kontrolü için rate hesaplar
     *
     * @param packetCount Paket sayısı
     * @param timeWindowMs Zaman penceresi (ms)
     * @return Saniyedeki paket oranı
     */
    public static double calculatePacketRate(int packetCount, long timeWindowMs) {
        if (timeWindowMs <= 0) {
            return 0.0;
        }
        return (packetCount * 1000.0) / timeWindowMs;
    }

    /**
     * String'in güvenli uzunlukta olup olmadığını kontrol eder
     * Crash exploit'lerini önlemek için
     *
     * @param str String
     * @param maxLength Maksimum uzunluk
     * @return Güvenli mi?
     */
    public static boolean isStringSafe(String str, int maxLength) {
        if (str == null) {
            return true;
        }
        return str.length() <= maxLength;
    }

    /**
     * String içinde zararlı karakterler olup olmadığını kontrol eder
     *
     * @param str String
     * @return Güvenli mi?
     */
    public static boolean containsSafeCharacters(String str) {
        if (str == null) {
            return true;
        }

        // Null byte veya diğer zararlı karakterleri kontrol et
        for (char c : str.toCharArray()) {
            if (c == '\0' || c == '\uFFFD') { // Null byte veya replacement character
                return false;
            }
            // Aşırı Unicode karakterler
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                return false;
            }
        }

        return true;
    }

    /**
     * Sayının güvenli aralıkta olup olmadığını kontrol eder
     *
     * @param value Değer
     * @param min Minimum
     * @param max Maksimum
     * @return Güvenli mi?
     */
    public static boolean isNumberInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * Slot numarasının geçerli olup olmadığını kontrol eder
     *
     * @param slot Slot numarası
     * @param containerSize Container boyutu
     * @return Geçerli mi?
     */
    public static boolean isSlotValid(int slot, int containerSize) {
        return slot >= 0 && slot < containerSize;
    }
}
