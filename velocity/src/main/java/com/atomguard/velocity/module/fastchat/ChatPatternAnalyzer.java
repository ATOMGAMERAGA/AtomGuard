package com.atomguard.velocity.module.fastchat;

import java.util.regex.Pattern;

public class ChatPatternAnalyzer {

    private static final Pattern URL_PATTERN = Pattern.compile(
        "(https?://|www\\.)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
        Pattern.CASE_INSENSITIVE);
    
    public boolean isCapsSpam(String message, double thresholdPercent, int minLength) {
        if (message.length() < minLength) return false;
        
        long capsCount = message.chars().filter(Character::isUpperCase).count();
        double percent = (double) capsCount / message.length() * 100.0;
        
        return percent >= thresholdPercent;
    }

    public boolean hasRepeatedCharacters(String message, int threshold) {
        if (message == null || message.isEmpty()) return false;
        if (threshold < 2) return false;

        int count = 1;
        char lastChar = message.charAt(0);

        for (int i = 1; i < message.length(); i++) {
            char currentChar = message.charAt(i);
            if (currentChar == lastChar) {
                count++;
                if (count >= threshold) {
                    return true;
                }
            } else {
                count = 1;
                lastChar = currentChar;
            }
        }
        return false;
    }

    public boolean containsLink(String message) {
        return URL_PATTERN.matcher(message).find();
    }
}