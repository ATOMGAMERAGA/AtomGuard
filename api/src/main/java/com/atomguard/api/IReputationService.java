package com.atomguard.api;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * IP reputation servisi arayüzü.
 * IP adreslerinin güvenilirlik kontrolünü sağlar.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public interface IReputationService {

    /**
     * Bir IP adresinin VPN/Proxy olup olmadığını kontrol eder.
     *
     * @param ipAddress Kontrol edilecek IP adresi
     * @return VPN/Proxy ise true
     */
    boolean isVPN(@NotNull String ipAddress);

    /**
     * Bir IP adresinin engelli olup olmadığını kontrol eder.
     *
     * @param ipAddress Kontrol edilecek IP adresi
     * @return Engelli ise true
     */
    boolean isBlocked(@NotNull String ipAddress);

    /**
     * Bir IP adresini engel listesine ekler.
     *
     * @param ipAddress Engellenecek IP adresi
     */
    void blockIP(@NotNull String ipAddress);

    /**
     * Bir IP adresini engel listesinden kaldırır.
     *
     * @param ipAddress Kaldırılacak IP adresi
     */
    void unblockIP(@NotNull String ipAddress);

    /**
     * Tüm engelli IP adreslerini alır.
     *
     * @return Engelli IP listesi
     */
    @NotNull
    Set<String> getBlockedIPs();

    /**
     * Bir IP adresinin beyaz listede olup olmadığını kontrol eder.
     *
     * @param ipAddress Kontrol edilecek IP adresi
     * @return Beyaz listede ise true
     */
    boolean isWhitelisted(@NotNull String ipAddress);
}
