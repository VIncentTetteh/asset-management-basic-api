package com.assetiq.security.sso;

import java.util.Locale;
import java.util.Set;

/**
 * Which email domains may ever route SSO discovery.
 *
 * <p>A public mailbox provider is shared by everyone, so no organisation can own
 * one: routing gmail.com to a tenant's IdP would hand that tenant the login of
 * every Gmail user who typed their address. These are refused whatever the
 * verification state says.
 */
public final class EmailDomains {

    /** Consumer mailbox providers, plus the throwaway domains that behave like them. */
    private static final Set<String> PUBLIC_PROVIDERS = Set.of(
            "gmail.com", "googlemail.com",
            "outlook.com", "hotmail.com", "live.com", "msn.com",
            "yahoo.com", "yahoo.co.uk", "ymail.com",
            "icloud.com", "me.com", "mac.com",
            "proton.me", "protonmail.com", "pm.me",
            "aol.com", "gmx.com", "gmx.net", "mail.com", "zoho.com",
            "yandex.com", "yandex.ru", "qq.com", "163.com", "126.com",
            "mail.ru", "inbox.com", "fastmail.com", "hey.com",
            "example.com", "example.org", "example.net",
            "mailinator.com", "yopmail.com", "guerrillamail.com", "10minutemail.com");

    private EmailDomains() {
    }

    /** Lower-cased and trimmed, or null when there is nothing there. */
    public static String normalise(String raw) {
        if (raw == null) return null;
        String domain = raw.trim().toLowerCase(Locale.ROOT);
        return domain.isEmpty() ? null : domain;
    }

    /** True for a domain no organisation may claim, however it was verified. */
    public static boolean isPublicProvider(String raw) {
        String domain = normalise(raw);
        return domain != null && PUBLIC_PROVIDERS.contains(domain);
    }
}
