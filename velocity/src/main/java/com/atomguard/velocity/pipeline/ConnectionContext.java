package com.atomguard.velocity.pipeline;

import java.util.Optional;
import java.util.UUID;

public record ConnectionContext(
    String ip,
    String username,
    @Nullable UUID uuid,
    @Nullable String hostname,
    int port,
    int protocol
) {
    public @interface Nullable {}
}
