package com.atomguard.velocity.module.iptables;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IPTablesRuleStore {

    private final Map<String, IPTablesRule> activeRules = new ConcurrentHashMap<>();

    public void addRule(String key, IPTablesRule rule) {
        activeRules.put(key, rule);
    }

    public void removeRule(String key) {
        activeRules.remove(key);
    }

    public IPTablesRule getRule(String key) {
        return activeRules.get(key);
    }

    public List<IPTablesRule> getExpiredRules() {
        long now = System.currentTimeMillis();
        return activeRules.values().stream()
                .filter(rule -> rule.getExpiry() < now)
                .collect(Collectors.toList());
    }

    public List<IPTablesRule> getAllRules() {
        return new ArrayList<>(activeRules.values());
    }
    
    public void clear() {
        activeRules.clear();
    }
}