package com.atomguard.module.honeypot;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Sahte Minecraft Server List Ping (SLP) yanıtları gönderir.
 * Port tarayıcılarını ve botları çekmek için gerçek bir 1.21.4 sunucusunu taklit eder.
 *
 * <p>Gelen TCP bağlantısı için en az 1 byte okumaya çalışır; ardından
 * Minecraft protokol formatında sahte bir status JSON'u yanıtlar. Tam
 * protokol uyumu yerine temel tarayıcıları kandıracak kadar geçerli bir
 * yanıt oluşturur.</p>
 */
public class FakeMotdHandler {

    private final String serverName;
    private final int maxPlayers;
    private final int onlinePlayers;
    private final String version;
    private final int protocol;

    /**
     * @param serverName   MOTD metninde görünecek sunucu adı (Minecraft renk kodları dahil)
     * @param maxPlayers   Sahte maksimum oyuncu sayısı
     * @param onlinePlayers Sahte mevcut online oyuncu sayısı
     * @param version      Gösterilecek sürüm dizisi (örn. "1.21.4")
     * @param protocol     Minecraft protokol numarası (1.21.4 = 769)
     */
    public FakeMotdHandler(String serverName, int maxPlayers, int onlinePlayers,
                           String version, int protocol) {
        this.serverName = serverName;
        this.maxPlayers = maxPlayers;
        this.onlinePlayers = onlinePlayers;
        this.version = version;
        this.protocol = protocol;
    }

    /**
     * Bir TCP socket bağlantısını SLP isteği olarak işlemeye çalışır.
     *
     * @param socket Gelen bağlantının soketi (zaman aşımı çağıran tarafından ayarlanmalıdır)
     * @return Bağlantı başarıyla işlendiyse {@code true}, aksi hâlde {@code false}
     */
    public boolean handle(Socket socket) {
        try {
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // En az 1 byte oku — bağlantının gerçek bir istemciden geldiğini doğrular
            int firstByte = in.read();
            if (firstByte == -1) return false;

            String jsonResponse = buildStatusJson();
            sendStatusResponse(out, jsonResponse);
            return true;

        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Vanilla Minecraft status JSON yanıtını oluşturur.
     */
    private String buildStatusJson() {
        // JSON özel karakterlerden kaç
        String safeName = serverName
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        String safeVersion = version
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        return String.format(
                "{\"version\":{\"name\":\"%s\",\"protocol\":%d}," +
                "\"players\":{\"max\":%d,\"online\":%d,\"sample\":[]}," +
                "\"description\":{\"text\":\"%s\"}," +
                "\"favicon\":\"\"}",
                safeVersion, protocol, maxPlayers, onlinePlayers, safeName
        );
    }

    /**
     * Minecraft SLP protokolüne uygun format ile status yanıtını gönderir.
     *
     * <p>Yapı: [paket uzunluğu VarInt] [0x00 paket ID] [string uzunluğu VarInt] [UTF-8 JSON baytları]</p>
     */
    private void sendStatusResponse(OutputStream out, String json) throws IOException {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        byte[] strLenVarInt = encodeVarInt(jsonBytes.length);

        // Paket içeriği: 1 (ID baytı) + varInt(string len) + string baytları
        int packetDataLen = 1 + strLenVarInt.length + jsonBytes.length;
        byte[] packetLenVarInt = encodeVarInt(packetDataLen);

        DataOutputStream dos = new DataOutputStream(out);
        dos.write(packetLenVarInt);  // paket uzunluğu
        dos.write(0x00);             // paket ID: Status Response
        dos.write(strLenVarInt);     // string uzunluğu
        dos.write(jsonBytes);        // JSON veri
        dos.flush();
    }

    /**
     * Bir tam sayıyı Minecraft VarInt formatında kodlar.
     *
     * @param value Kodlanacak değer (negatif olmamalıdır)
     * @return VarInt bayt dizisi (1–5 bayt)
     */
    private byte[] encodeVarInt(int value) {
        byte[] buf = new byte[5];
        int i = 0;
        do {
            byte temp = (byte) (value & 0x7F);
            value >>>= 7;
            if (value != 0) temp |= 0x80;
            buf[i++] = temp;
        } while (value != 0);
        byte[] result = new byte[i];
        System.arraycopy(buf, 0, result, 0, i);
        return result;
    }

    // Getters
    public String getServerName() { return serverName; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getOnlinePlayers() { return onlinePlayers; }
    public String getVersion() { return version; }
    public int getProtocol() { return protocol; }
}
