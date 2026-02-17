package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.IPUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CIDR aralığı tabanlı IP engelleme.
 */
public class CIDRBlocker {

    private final CopyOnWriteArrayList<CIDRRange> ranges = new CopyOnWriteArrayList<>();

    public void addRange(String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return;
            int network = IPUtils.ipToInt(parts[0]);
            int prefix = Integer.parseInt(parts[1]);
            int mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
            ranges.add(new CIDRRange(cidr, network & mask, mask));
        } catch (Exception ignored) {}
    }

    public void addRanges(List<String> cidrs) {
        cidrs.forEach(this::addRange);
    }

    public boolean isBlocked(String ip) {
        try {
            int ipInt = IPUtils.ipToInt(ip);
            for (CIDRRange range : ranges) {
                if ((ipInt & range.mask()) == range.network()) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void clear() { ranges.clear(); }
    public int size() { return ranges.size(); }

    private record CIDRRange(String cidr, int network, int mask) {}
}
