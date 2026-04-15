package com.assetiq.services;

import java.util.List;
import java.util.Map;

public interface EmailService {
    void sendTemplate(String to, String subject, String template, Map<String, Object> model);

    default void sendTemplate(List<String> to, String subject, String template, Map<String, Object> model) {
        if (to == null) return;
        for (String recipient : to) {
            sendTemplate(recipient, subject, template, model);
        }
    }
}
