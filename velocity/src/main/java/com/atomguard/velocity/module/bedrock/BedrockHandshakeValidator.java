package com.atomguard.velocity.module.bedrock;

import com.atomguard.velocity.AtomGuardVelocity;

/**
 * Bedrock oyuncularinin handshake pattern'lerini dogrular.
 *
 * <p>Bedrock oyuncularin farkli handshake yapisi (Geyser/Floodgate uzerinden)
 * normal Java Edition handshake'lerinden ayirt edilir.</p>
 */
public class BedrockHandshakeValidator {

    private final AtomGuardVelocity plugin;

    public BedrockHandshakeValidator(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    /**
     * Handshake verisinin gecerli bir Bedrock/Geyser baglantisi olup olmadigini kontrol eder.
     *
     * @param hostname Handshake hostname alani
     * @param port     Handshake port alani
     * @return true ise gecerli Bedrock handshake
     */
    public boolean isValidBedrockHandshake(String hostname, int port) {
        if (hostname == null) return false;

        // Floodgate handshake'leri genelde ek bilgi icerir
        // Format: hostname\0FML\0 veya Geyser proxy bilgisi
        if (hostname.contains("\0")) {
            // Geyser/Floodgate ekstra veri ekler
            return true;
        }

        // Floodgate hostname prefix kontrolu
        // Bazi yapilandirmalarda Bedrock oyunculari ozel hostname ile gelir
        return hostname.startsWith("***") || hostname.contains("Geyser");
    }

    /**
     * Bedrock oyuncu protokol versiyonunun desteklenen aralikta olup olmadigini kontrol eder.
     *
     * @param protocolVersion Oyuncu protokol versiyonu
     * @return true ise desteklenen versiyon
     */
    public boolean isSupportedProtocolVersion(int protocolVersion) {
        // Geyser 1.21.x icin genelde Java Edition protokol versiyonunu proxy eder (768+)
        // Ancak bazi eski Geyser versiyonlari farkli versiyon numarasi gonderebilir
        return protocolVersion >= 760 && protocolVersion <= 800;
    }
}
