package com.assetiq.services;

import java.util.List;
import java.util.Map;

public interface EmailService {
    void sendTemplate(String to, String subject, String template, Map<String, Object> model);

    /**
     * Whether this environment can actually deliver mail.
     *
     * <p>{@link #sendTemplate} is deliberately silent and asynchronous — a hung
     * SMTP socket must not fail the request that triggered it — which means a
     * caller cannot tell a sent message from a dropped one. Flows whose answer to
     * the user depends on it (an invitation link the recipient must receive, say)
     * ask here first, so they can offer the link another way instead of claiming
     * a mail is on its way that nobody will ever receive.
     *
     * <p>False does not promise delivery; it only reports that sending is off or
     * unconfigured. True means an attempt will be made.
     */
    boolean isEnabled();

    default void sendTemplate(List<String> to, String subject, String template, Map<String, Object> model) {
        if (to == null) return;
        for (String recipient : to) {
            sendTemplate(recipient, subject, template, model);
        }
    }
}
