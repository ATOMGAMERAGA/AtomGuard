package com.atomguard.velocity.event;

public record VelocityIPBlockedEvent(String ip, String reason, String module) {}
