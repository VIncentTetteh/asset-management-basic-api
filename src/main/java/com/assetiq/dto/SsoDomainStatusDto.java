package com.assetiq.dto;

/**
 * Where an organisation's SSO email domain stands.
 *
 * @param emailDomain    the claimed domain, or null when none is set
 * @param verified       true once the claim was proved; only then does discovery route on it
 * @param publicProvider true for a shared mailbox provider, which is never routable
 * @param txtRecordName  the DNS name to publish the record on
 * @param txtRecordValue the exact TXT value to publish ({@code assetiq-verify=<token>})
 */
public record SsoDomainStatusDto(
        String emailDomain,
        boolean verified,
        boolean publicProvider,
        String txtRecordName,
        String txtRecordValue) {
}
