package com.atomguard.forensics;

/**
 * Tek bir kaydedilmis paket olayini temsil eden kayit.
 *
 * @param timestamp  Paketin kaydedildigi zaman damgasi (epoch ms)
 * @param packetType Paket tipi adi (ornegin "PLAY_CLIENT_INTERACT_ENTITY")
 * @param incoming   true ise istemciden sunucuya, false ise sunucudan istemciye
 * @param dataSize   Paket veri boyutu (byte)
 * @param summary    Paketin kisa ozet bilgisi
 *
 * @author AtomGuard Team
 * @version 1.3.0
 */
public record PacketRecording(
    long timestamp,
    String packetType,
    boolean incoming,
    int dataSize,
    String summary
) {}
