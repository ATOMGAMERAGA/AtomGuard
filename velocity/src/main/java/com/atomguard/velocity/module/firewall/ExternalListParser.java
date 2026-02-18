package com.atomguard.velocity.module.firewall;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExternalListParser {

    // Simple parser for plain text IPs or CIDRs
    // Matches standard IPv4 and CIDR
    private static final Pattern IP_CIDR_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?:/\\d{1,2})?\\b");

    public List<String> parse(List<String> rawLines, String format) {
        List<String> parsed = new ArrayList<>();
        
        for (String line : rawLines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            // Simple extraction
            Matcher matcher = IP_CIDR_PATTERN.matcher(line);
            if (matcher.find()) {
                parsed.add(matcher.group());
            }
        }
        return parsed;
    }
}