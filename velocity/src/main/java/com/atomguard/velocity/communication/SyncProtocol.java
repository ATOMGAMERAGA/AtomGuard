package com.atomguard.velocity.communication;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Velocity ↔ Core iletişim protokolü.
 * Format: version(byte) + type(short) + length(int) + payload(UTF-8)
 */
public final class SyncProtocol {

    public static final String CHANNEL = "atomguard:main";
    public static final byte VERSION = 1;

    public enum MessageType {
        PLAYER_DATA_RESPONSE(0),
        PLAYER_VERIFIED(1),
        ATTACK_MODE_SYNC(2),
        IP_BLOCK_SYNC(3),
        IP_UNBLOCK_SYNC(4),
        THREAT_SCORE(5),
        PLAYER_DATA_REQUEST(6),
        STATS_SYNC(7),
        CONFIG_RELOAD(8),
        KICK_PLAYER(9),
        BAN_IP(10);

        public final short id;
        MessageType(int id) { this.id = (short) id; }
    }

    private SyncProtocol() {}

    public static byte[] encode(MessageType type, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(7 + payloadBytes.length);
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(VERSION);
        dos.writeShort(type.id);
        dos.writeInt(payloadBytes.length);
        dos.write(payloadBytes);
        return baos.toByteArray();
    }

    public static DecodedMessage decode(byte[] data) throws IOException {
        if (data.length < 7) throw new IOException("Paket çok kısa");
        int version = data[0] & 0xFF;
        if (version != VERSION) throw new IOException("Desteklenmeyen protokol sürümü: " + version);
        short typeId = (short) ((data[1] << 8) | (data[2] & 0xFF));
        int length = ((data[3] & 0xFF) << 24) | ((data[4] & 0xFF) << 16) | ((data[5] & 0xFF) << 8) | (data[6] & 0xFF);
        if (data.length < 7 + length) throw new IOException("Eksik veri");
        String payload = new String(data, 7, length, StandardCharsets.UTF_8);

        MessageType type = null;
        for (MessageType t : MessageType.values()) {
            if (t.id == typeId) { type = t; break; }
        }
        if (type == null) throw new IOException("Bilinmeyen mesaj tipi: " + typeId);
        return new DecodedMessage(type, payload);
    }

    public record DecodedMessage(MessageType type, String payload) {}
}
