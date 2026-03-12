package com.atomguard.velocity.module.firewall;

/**
 * Güvenlik duvarı istisna türleri — IP, CIDR, ülke, ASN veya kullanıcı adı bazında istisna tanımlar.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public enum ExceptionType {
    IP, CIDR, COUNTRY, ASN, USERNAME
}
