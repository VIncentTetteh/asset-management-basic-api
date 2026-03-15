package com.example.demo.services.impl;

import com.example.demo.services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final TemplateEngine templateEngine;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.email.from:no-reply@assetiq.local}")
    private String fromAddress;

    @Value("${app.email.from-name:AssetIQ}")
    private String fromName;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String baseUrl;

    @Value("${app.email.support:support@assetiq.local}")
    private String supportEmail;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    public EmailServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider, TemplateEngine templateEngine) {
        this.mailSenderProvider = mailSenderProvider;
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendTemplate(String to, String subject, String template, Map<String, Object> model) {
        if (to == null || to.isBlank()) {
            return;
        }
        if (!emailEnabled) {
            log.debug("[EMAIL] Skipped sending to {} (disabled)", to);
            return;
        }
        if (smtpHost == null || smtpHost.isBlank()) {
            log.warn("[EMAIL] Skipped sending to {} (SMTP host not configured)", to);
            return;
        }

        try {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                log.warn("[EMAIL] Skipped sending to {} (JavaMailSender not configured)", to);
                return;
            }
            Map<String, Object> payload = new HashMap<>();
            if (model != null) payload.putAll(model);
            payload.putIfAbsent("appName", fromName);
            payload.putIfAbsent("baseUrl", baseUrl);
            payload.putIfAbsent("supportEmail", supportEmail);

            Context context = new Context();
            context.setVariables(payload);
            String html = templateEngine.process(template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setFrom(fromAddress, fromName);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
        } catch (Exception e) {
            log.warn("[EMAIL] Failed to send to {}: {}", to, e.getMessage());
        }
    }
}
