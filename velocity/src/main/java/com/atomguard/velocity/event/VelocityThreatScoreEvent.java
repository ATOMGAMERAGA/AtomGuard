package com.atomguard.velocity.event;

public record VelocityThreatScoreEvent(String ip, int oldScore, int newScore, String reason) {}
