package com.assetiq.security.sso;

import java.util.List;

/**
 * Reads the TXT records of a domain. An interface so domain verification can be
 * tested without a name server, and so a deployment with no outbound DNS can
 * fall back to the operator-approval path instead.
 */
public interface DnsTxtResolver {

    /**
     * The TXT records published on {@code domain}, or an empty list when the
     * lookup finds nothing or fails. Never throws: a lookup that cannot be made
     * means "not verified", not an error the caller has to handle.
     */
    List<String> txtRecords(String domain);
}
