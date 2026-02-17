package com.atomguard.velocity.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * IP adresi yardımcı metodları.
 */
public final class IPUtils {

    private IPUtils() {}

    /**
     * Noktalı ondalık IP adresini int'e çevirir.
     */
    public static int ipToInt(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return 0;
            int result = 0;
            for (String part : parts) {
                result = (result << 8) | (Integer.parseInt(part) & 0xFF);
            }
            return result;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Int'i noktalı ondalık IP adresine çevirir.
     */
    public static String intToIp(int ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." +
               ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
    }

    /**
     * IP adresinin CIDR aralığında olup olmadığını kontrol eder.
     */
    public static boolean isInCIDR(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;
            int networkAddr = ipToInt(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            int mask = prefixLength == 0 ? 0 : (0xFFFFFFFF << (32 - prefixLength));
            int ipInt = ipToInt(ip);
            return (ipInt & mask) == (networkAddr & mask);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * IP adresinin /24 subnet'ini döndürür (örn: "192.168.1").
     */
    public static String getSubnet24(String ip) {
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) : ip;
    }

    /**
     * IP adresinin /16 subnet'ini döndürür (örn: "192.168").
     */
    public static String getSubnet16(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length >= 2) return parts[0] + "." + parts[1];
        return ip;
    }

    /**
     * IP adresinin özel/loopback olup olmadığını kontrol eder.
     */
    public static boolean isPrivateIP(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() ||
                   addr.isLinkLocalAddress() || addr.isAnyLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * IP adres formatını doğrular.
     */
    public static boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                int val = Integer.parseInt(part);
                if (val < 0 || val > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * DNSBL sorgusu için IP oktetlerini tersine çevirir.
     * Örn: "1.2.3.4" → "4.3.2.1"
     */
    public static String reverseIP(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return ip;
        return parts[3] + "." + parts[2] + "." + parts[1] + "." + parts[0];
    }

    /**
     * InetSocketAddress'ten IP string'i çıkarır.
     */
    public static String extractIP(InetSocketAddress address) {
        if (address == null) return "unknown";
        InetAddress addr = address.getAddress();
        return addr != null ? addr.getHostAddress() : address.getHostString();
    }
}
