package com.atomguard.velocity.pipeline;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Gelen bağlantının bağlam bilgileri.
 * <p>
 * {@code verified=true} ise bu IP daha önce başarılı giriş yapmış;
 * pipeline içinde soft/medium check'ler bu IP için atlanır.
 */
public record ConnectionContext(
    String ip,
    String username,
    @Nullable UUID uuid,
    @Nullable String hostname,
    int port,
    int protocol,
    boolean verified
) {
    /** Geriye uyumluluk: verified=false ile oluşturur. */
    public ConnectionContext(String ip, String username, UUID uuid, String hostname, int port, int protocol) {
        this(ip, username, uuid, hostname, port, protocol, false);
    }
}
