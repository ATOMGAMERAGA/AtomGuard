package com.atomguard.listener;

import com.atomguard.AtomGuard;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Netty pipeline'ına enjekte edilen crash koruması handler'ı.
 * "decoder" handler'ından SONRA yerleştirilir.
 *
 * Bu handler, dekoder tarafından parse edilen paketlerdeki hataları yakalar
 * ve sunucu crash'lerini önler. NaN, Infinity gibi değerler ve
 * malformed paketler bu katmanda engellenir.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class NettyCrashHandler extends ChannelDuplexHandler {

    /** Handler pipeline adı — tekil olmalı */
    public static final String HANDLER_NAME = "atomguard-crash-handler";

    private final AtomGuard plugin;
    private final UUID playerUuid;
    private final String playerName;

    // Exception tracking
    private long lastExceptionTime = 0;
    private int exceptionCount = 0;

    /**
     * NettyCrashHandler constructor
     *
     * @param plugin     Ana plugin instance
     * @param playerUuid Oyuncu UUID'si
     * @param playerName Oyuncu adı (loglama için)
     */
    public NettyCrashHandler(@NotNull AtomGuard plugin,
                             @NotNull UUID playerUuid,
                             @NotNull String playerName) {
        this.plugin = plugin;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
    }

    /**
     * Gelen mesajları (dekode edilmiş paketleri) dinler.
     * Hata oluşursa bağlantıyı güvenli şekilde kapatır.
     */
    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
        try {
            super.channelRead(ctx, msg);
        } catch (Throwable t) {
            exceptionCaught(ctx, t);
        }
    }

    /**
     * Pipeline'da oluşan hataları yakalar.
     * DecoderException, overflow vb. hatalar sunucuyu çökertebilir.
     */
    @Override
    public void exceptionCaught(@NotNull ChannelHandlerContext ctx, @NotNull Throwable cause) {
        // Crash girişimini tespit et
        String errorMsg = cause.getMessage();
        boolean isCrashAttempt = false;

        // Exception sayacı kontrolü
        long now = System.currentTimeMillis();
        if (now - lastExceptionTime < 1000) {
            exceptionCount++;
        } else {
            exceptionCount = 1;
        }
        lastExceptionTime = now;

        if (errorMsg != null) {
            String lowerMsg = errorMsg.toLowerCase();
            // Bilinen crash vektörleri (Genişletilmiş liste + NEW-06)
            isCrashAttempt = lowerMsg.contains("overflow")
                    || lowerMsg.contains("nan")
                    || lowerMsg.contains("infinity")
                    || lowerMsg.contains("out of bounds")
                    || lowerMsg.contains("negative length")
                    || lowerMsg.contains("varint too big")
                    || lowerMsg.contains("string too long")
                    || lowerMsg.contains("packet too large")
                    || lowerMsg.contains("bad packet")
                    || lowerMsg.contains("received string length")
                    || lowerMsg.contains("tried to read")
                    || lowerMsg.contains("decompress") // NEW-06: Compression Bomb
                    || lowerMsg.contains("inflation")   // NEW-06: Zlib inflation error
                    || lowerMsg.contains("encryption")
                    || lowerMsg.contains("recursion")
                    || lowerMsg.contains("stack overflow")
                    || lowerMsg.contains("buffer")
                    || lowerMsg.contains("slice")
                    || lowerMsg.contains("index");
        }
        
        // StackOverflowError kontrolü (tür bazlı)
        if (cause instanceof StackOverflowError) {
            isCrashAttempt = true;
        }

        // DecoderException ise bilinen crash vektörlerini kontrol et
        if (cause.getClass().getSimpleName().contains("DecoderException") && errorMsg != null) {
            String lowerCauseMsg = errorMsg.toLowerCase();
            if (lowerCauseMsg.contains("overflow") || lowerCauseMsg.contains("varint")
                    || lowerCauseMsg.contains("too big") || lowerCauseMsg.contains("too large")
                    || lowerCauseMsg.contains("negative length") || lowerCauseMsg.contains("bad packet id")) {
                isCrashAttempt = true;
            }
        }

        // Çok sık exception fırlatan oyuncuyu at (3+ exception / saniye)
        if (exceptionCount > 3) {
            isCrashAttempt = true;
            plugin.getLogManager().logExploit(playerName, "netty-flood", "Çok fazla paket hatası (" + exceptionCount + "/sn)");
        }

        if (isCrashAttempt) {
            // Crash girişimini logla
            plugin.getLogManager().logExploit(playerName, "netty-crash",
                    String.format("Netty crash girişimi tespit edildi! Hata: %s, UUID: %s",
                            cause.getClass().getSimpleName() + ": " + errorMsg,
                            playerUuid));

            // Bağlantıyı güvenli şekilde kapat
            if (ctx.channel().isActive()) {
                ctx.close();
            }
            return;
        }

        // Bilinmeyen hatalar için — pipeline'daki sonraki handler'a ilet
        // Bağlantıyı kapatma, normal ağ hataları olabilir
        try {
            ctx.fireExceptionCaught(cause);
        } catch (Exception e) {
            // Logla ama çökme
        }
    }

    /**
     * Channel inaktif olduğunda temizlik yapar
     */
    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    /**
     * Oyuncu UUID'sini döndürür
     */
    @NotNull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Oyuncu adını döndürür
     */
    @NotNull
    public String getPlayerName() {
        return playerName;
    }
}