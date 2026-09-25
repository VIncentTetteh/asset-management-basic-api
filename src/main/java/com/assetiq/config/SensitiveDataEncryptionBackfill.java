package com.assetiq.config;

import com.assetiq.models.BillingPayment;
import com.assetiq.models.OrgSsoConfig;
import com.assetiq.models.OrganisationSubscription;
import com.assetiq.models.User;
import com.assetiq.models.Webhook;
import com.assetiq.repositories.BillingPaymentRepository;
import com.assetiq.repositories.OrgSsoConfigRepository;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.repositories.WebhookRepository;
import com.assetiq.security.SecretCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Idempotently upgrades legacy plaintext secrets after Flyway widens their columns. */
@Component
@Order(200)
public class SensitiveDataEncryptionBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataEncryptionBackfill.class);

    private final SecretCryptoService crypto;
    private final UserRepository userRepository;
    private final OrgSsoConfigRepository ssoRepository;
    private final WebhookRepository webhookRepository;
    private final BillingPaymentRepository paymentRepository;
    private final OrganisationSubscriptionRepository subscriptionRepository;

    public SensitiveDataEncryptionBackfill(
            SecretCryptoService crypto,
            UserRepository userRepository,
            OrgSsoConfigRepository ssoRepository,
            WebhookRepository webhookRepository,
            BillingPaymentRepository paymentRepository,
            OrganisationSubscriptionRepository subscriptionRepository) {
        this.crypto = crypto;
        this.userRepository = userRepository;
        this.ssoRepository = ssoRepository;
        this.webhookRepository = webhookRepository;
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!crypto.isConfigured()) {
            log.warn("[SECURITY] Data-encryption backfill skipped because APP_DATA_ENCRYPTION_KEY is not configured");
            return;
        }

        int updated = migrateUsers() + migrateSso() + migrateWebhooks()
                + migratePayments() + migrateSubscriptions();
        if (updated > 0) {
            log.info("[SECURITY] Protected or purged sensitive fields on {} database records", updated);
        }
    }

    private int migrateUsers() {
        List<User> changed = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            if (hasPlaintext(user.getMfaSecret())) {
                user.setMfaSecret(crypto.encrypt(user.getMfaSecret()));
                changed.add(user);
            }
        }
        if (!changed.isEmpty()) userRepository.saveAll(changed);
        return changed.size();
    }

    private int migrateSso() {
        List<OrgSsoConfig> changed = new ArrayList<>();
        for (OrgSsoConfig config : ssoRepository.findAll()) {
            if (hasPlaintext(config.getClientSecret())) {
                config.setClientSecret(crypto.encrypt(config.getClientSecret()));
                changed.add(config);
            }
        }
        if (!changed.isEmpty()) ssoRepository.saveAll(changed);
        return changed.size();
    }

    private int migrateWebhooks() {
        List<Webhook> changed = new ArrayList<>();
        for (Webhook webhook : webhookRepository.findAll()) {
            if (hasPlaintext(webhook.getSecret())) {
                webhook.setSecret(crypto.encrypt(webhook.getSecret()));
                changed.add(webhook);
            }
        }
        if (!changed.isEmpty()) webhookRepository.saveAll(changed);
        return changed.size();
    }

    private int migratePayments() {
        List<BillingPayment> changed = new ArrayList<>();
        for (BillingPayment payment : paymentRepository.findAll()) {
            boolean dirty = false;
            if (hasPlaintext(payment.getPaystackAuthorizationCode())) {
                payment.setPaystackAuthorizationCode(crypto.encrypt(payment.getPaystackAuthorizationCode()));
                dirty = true;
            }
            if (hasPlaintext(payment.getPaystackEmailToken())) {
                payment.setPaystackEmailToken(crypto.encrypt(payment.getPaystackEmailToken()));
                dirty = true;
            }
            if (payment.getRawGatewayPayload() != null) {
                payment.setRawGatewayPayload(null);
                dirty = true;
            }
            if (dirty) changed.add(payment);
        }
        if (!changed.isEmpty()) paymentRepository.saveAll(changed);
        return changed.size();
    }

    private int migrateSubscriptions() {
        List<OrganisationSubscription> changed = new ArrayList<>();
        for (OrganisationSubscription subscription : subscriptionRepository.findAll()) {
            if (hasPlaintext(subscription.getPaystackEmailToken())) {
                subscription.setPaystackEmailToken(crypto.encrypt(subscription.getPaystackEmailToken()));
                changed.add(subscription);
            }
        }
        if (!changed.isEmpty()) subscriptionRepository.saveAll(changed);
        return changed.size();
    }

    private boolean hasPlaintext(String value) {
        return value != null && !value.isBlank() && !crypto.isEncrypted(value);
    }
}
