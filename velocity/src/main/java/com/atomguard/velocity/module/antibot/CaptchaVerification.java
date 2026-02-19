package com.atomguard.velocity.module.antibot;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sohbet tabanlı CAPTCHA doğrulaması - bot önleme.
 */
public class CaptchaVerification {

    private final Map<UUID, CaptchaSession> pending = new ConcurrentHashMap<>();
    private final Set<UUID> verified = ConcurrentHashMap.newKeySet();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final SecureRandom random = new SecureRandom();
    private final int timeoutSeconds;

    public CaptchaVerification(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public void sendCaptcha(Player player) {
        String answer = String.valueOf(random.nextInt(9000) + 1000);
        int a = random.nextInt(50) + 1;
        int b = random.nextInt(50) + 1;
        String question = a + " + " + b + " = ?";
        String correctAnswer = String.valueOf(a + b);

        pending.put(player.getUniqueId(), new CaptchaSession(correctAnswer, System.currentTimeMillis()));

        player.sendMessage(mm.deserialize(
            "<yellow>┌─────────────────────────────────┐\n" +
            "│  <gold><bold>CAPTCHA Doğrulaması</bold></gold>           │\n" +
            "│  Lütfen hesaplayın: <white>" + question + "</white>    │\n" +
            "│  Cevabı sohbete yazın.           │\n" +
            "└─────────────────────────────────┘</yellow>"
        ));
    }

    public boolean checkAnswer(UUID playerId, String answer) {
        CaptchaSession session = pending.get(playerId);
        if (session == null) return false;

        if (System.currentTimeMillis() - session.startTime() > timeoutSeconds * 1000L) {
            pending.remove(playerId);
            return false;
        }

        if (session.answer().equals(answer.trim())) {
            pending.remove(playerId);
            verified.add(playerId);
            return true;
        }
        return false;
    }

    public boolean hasPendingCaptcha(UUID playerId) {
        return pending.containsKey(playerId);
    }

    public boolean isVerified(UUID playerId) {
        return verified.contains(playerId);
    }

    public void onPlayerLeave(UUID playerId) {
        pending.remove(playerId);
        verified.remove(playerId);
    }

    public void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - (timeoutSeconds * 1000L);
        pending.entrySet().removeIf(e -> e.getValue().startTime() < cutoff);
    }

    private record CaptchaSession(String answer, long startTime) {}
}
