package com.example.demo.services.impl;

import com.example.demo.dto.NotificationDto;
import com.example.demo.dto.NotificationPageDto;
import com.example.demo.dto.NotificationPreferencesDto;
import com.example.demo.enums.NotificationType;
import com.example.demo.models.Notification;
import com.example.demo.models.NotificationPreferences;
import com.example.demo.models.Organisation;
import com.example.demo.models.User;
import com.example.demo.repositories.NotificationPreferencesRepository;
import com.example.demo.repositories.NotificationRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.EmailService;
import com.example.demo.services.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String emailBaseUrl;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   NotificationPreferencesRepository preferencesRepository,
                                   UserRepository userRepository,
                                   EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.preferencesRepository = preferencesRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageDto getNotifications(
            User user,
            Organisation org,
            NotificationType type,
            Boolean read,
            int limit,
            long offset
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        long safeOffset = Math.max(0L, offset);
        Pageable pageable = PageRequest.of((int) (safeOffset / safeLimit), safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Notification> page;
        if (type != null && read != null) {
            page = notificationRepository.findByUserAndOrganisationAndTypeAndReadAndDeletedAtIsNull(user, org, type, read, pageable);
        } else if (type != null) {
            page = notificationRepository.findByUserAndOrganisationAndTypeAndDeletedAtIsNull(user, org, type, pageable);
        } else if (read != null) {
            page = notificationRepository.findByUserAndOrganisationAndReadAndDeletedAtIsNull(user, org, read, pageable);
        } else {
            page = notificationRepository.findByUserAndOrganisationAndDeletedAtIsNull(user, org, pageable);
        }

        long unreadCount = notificationRepository.countByUserAndOrganisationAndReadAndDeletedAtIsNull(user, org, false);

        NotificationPageDto dto = new NotificationPageDto();
        dto.setTotal(page.getTotalElements());
        dto.setUnreadCount(unreadCount);
        dto.setLimit(safeLimit);
        dto.setOffset(safeOffset);
        dto.setItems(page.getContent().stream().map(this::toDto).toList());
        return dto;
    }

    @Override
    public void markAsRead(UUID notificationId, User user, Organisation org) {
        Notification notification = notificationRepository
                .findByIdAndUserAndOrganisationAndDeletedAtIsNull(notificationId, user, org)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
    }

    @Override
    public int markAllAsRead(User user, Organisation org) {
        return notificationRepository.markAllReadByUserAndOrganisation(user, org, Instant.now());
    }

    @Override
    public void deleteNotification(UUID notificationId, User user, Organisation org) {
        Notification notification = notificationRepository
                .findByIdAndUserAndOrganisationAndDeletedAtIsNull(notificationId, user, org)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        notification.setDeletedAt(Instant.now());
        notificationRepository.save(notification);
    }

    @Override
    public int deleteAllNotifications(User user, Organisation org) {
        Pageable all = PageRequest.of(0, Integer.MAX_VALUE);
        Page<Notification> active = notificationRepository.findByUserAndOrganisationAndDeletedAtIsNull(user, org, all);
        Instant now = Instant.now();
        active.forEach(n -> n.setDeletedAt(now));
        notificationRepository.saveAll(active.getContent());
        return active.getNumberOfElements();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferencesDto getPreferences(User user) {
        NotificationPreferences prefs = preferencesRepository.findByUser(user)
                .orElseGet(() -> buildDefaultPreferences(user));
        return toPreferencesDto(prefs);
    }

    @Override
    public NotificationPreferencesDto updatePreferences(User user, Map<String, Object> updates) {
        NotificationPreferences prefs = preferencesRepository.findByUser(user)
                .orElseGet(() -> buildDefaultPreferences(user));

        Object emailNotifs = updates.get("emailNotifications");
        if (emailNotifs instanceof Map<?, ?> emailMap) {
            applyBooleanIfPresent(emailMap, "deprecation", v -> prefs.setEmailDeprecation(v));
            applyBooleanIfPresent(emailMap, "maintenance", v -> prefs.setEmailMaintenance(v));
            applyBooleanIfPresent(emailMap, "approval", v -> prefs.setEmailApproval(v));
            applyBooleanIfPresent(emailMap, "system", v -> prefs.setEmailSystem(v));
            applyBooleanIfPresent(emailMap, "transfer", v -> prefs.setEmailTransfer(v));
            applyBooleanIfPresent(emailMap, "disposal", v -> prefs.setEmailDisposal(v));
            applyBooleanIfPresent(emailMap, "purchase_order", v -> prefs.setEmailPurchaseOrder(v));
        }
        applyBooleanIfPresent(updates, "pushNotifications", v -> prefs.setPushNotifications(v));
        applyBooleanIfPresent(updates, "inAppNotifications", v -> prefs.setInAppNotifications(v));
        applyBooleanIfPresent(updates, "dailyDigest", v -> prefs.setDailyDigest(v));

        Object digestTime = updates.get("digestTime");
        if (digestTime instanceof String s && s.matches("\\d{2}:\\d{2}")) {
            prefs.setDigestTime(s);
        }

        preferencesRepository.save(prefs);
        return toPreferencesDto(prefs);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(User user, Organisation org) {
        long total = notificationRepository.countByUserAndOrganisationAndDeletedAtIsNull(user, org);
        long unread = notificationRepository.countByUserAndOrganisationAndReadAndDeletedAtIsNull(user, org, false);

        Map<String, Long> byType = new LinkedHashMap<>();
        for (NotificationType t : NotificationType.values()) {
            long count = notificationRepository.countByUserAndOrganisationAndType(user, org, t);
            if (count > 0) byType.put(t.name().toLowerCase(), count);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalNotifications", total);
        summary.put("unreadNotifications", unread);
        summary.put("byType", byType);
        return summary;
    }

    // ── Admin notification dispatch ────────────────────────────────────────────

    @Override
    public void notifyOrgAdmins(Organisation org, NotificationType type,
                                String title, String message, UUID entityId, String actionUrl) {
        // Notify both ORG_ADMIN and ADMIN users within the organisation
        List<User> admins = userRepository
                .findByOrganisationAndRole_NameContainingIgnoreCaseAndDeletedAtIsNull(org, "ADMIN");
        for (User admin : admins) {
            Notification n = new Notification();
            n.setUser(admin);
            n.setOrganisation(org);
            n.setType(type);
            n.setTitle(title);
            n.setMessage(message);
            n.setEntityId(entityId);
            n.setActionUrl(actionUrl);
            notificationRepository.save(n);

            if (admin.getEmail() != null && isEmailEnabled(admin, type)) {
                Map<String, Object> model = new HashMap<>();
                model.put("firstName", admin.getFirstName());
                model.put("title", title);
                model.put("message", message);
                model.put("type", type.name().toLowerCase());
                model.put("actionUrl", resolveActionUrl(actionUrl));
                emailService.sendTemplate(admin.getEmail(), title, "email/notification", model);
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private NotificationPreferences buildDefaultPreferences(User user) {
        NotificationPreferences prefs = new NotificationPreferences();
        prefs.setUser(user);
        return preferencesRepository.save(prefs);
    }

    private boolean isEmailEnabled(User user, NotificationType type) {
        NotificationPreferences prefs = preferencesRepository.findByUser(user)
                .orElseGet(() -> buildDefaultPreferences(user));
        return switch (type) {
            case DEPRECATION -> prefs.isEmailDeprecation();
            case MAINTENANCE -> prefs.isEmailMaintenance();
            case APPROVAL -> prefs.isEmailApproval();
            case SYSTEM -> prefs.isEmailSystem();
            case TRANSFER -> prefs.isEmailTransfer();
            case DISPOSAL -> prefs.isEmailDisposal();
            case PURCHASE_ORDER -> prefs.isEmailPurchaseOrder();
            case WARRANTY_EXPIRY -> false;
            case END_OF_LIFE -> false;
            case BUDGET_THRESHOLD -> false;
            case CHECKOUT -> false;
            case EXPENSE -> false;
            case LEASE_EXPIRY -> false;
            case INSURANCE_EXPIRY -> false;
        };
    }

    private String resolveActionUrl(String actionUrl) {
        if (actionUrl == null || actionUrl.isBlank()) return null;
        if (actionUrl.startsWith("http://") || actionUrl.startsWith("https://")) {
            return actionUrl;
        }
        return emailBaseUrl.replaceAll("/+$", "") + (actionUrl.startsWith("/") ? actionUrl : "/" + actionUrl);
    }

    private NotificationDto toDto(Notification n) {
        NotificationDto dto = new NotificationDto();
        dto.setNotificationId(n.getId());
        dto.setType(n.getType());
        dto.setTitle(n.getTitle());
        dto.setMessage(n.getMessage());
        dto.setEntityId(n.getEntityId());
        dto.setActionUrl(n.getActionUrl());
        dto.setRead(n.isRead());
        dto.setReadAt(n.getReadAt());
        dto.setCreatedAt(n.getCreatedAt());
        return dto;
    }

    private NotificationPreferencesDto toPreferencesDto(NotificationPreferences prefs) {
        Map<String, Boolean> emailMap = new LinkedHashMap<>();
        emailMap.put("deprecation", prefs.isEmailDeprecation());
        emailMap.put("maintenance", prefs.isEmailMaintenance());
        emailMap.put("approval", prefs.isEmailApproval());
        emailMap.put("system", prefs.isEmailSystem());
        emailMap.put("transfer", prefs.isEmailTransfer());
        emailMap.put("disposal", prefs.isEmailDisposal());
        emailMap.put("purchase_order", prefs.isEmailPurchaseOrder());

        NotificationPreferencesDto dto = new NotificationPreferencesDto();
        dto.setEmailNotifications(emailMap);
        dto.setPushNotifications(prefs.isPushNotifications());
        dto.setInAppNotifications(prefs.isInAppNotifications());
        dto.setDailyDigest(prefs.isDailyDigest());
        dto.setDigestTime(prefs.getDigestTime());
        return dto;
    }

    private void applyBooleanIfPresent(Map<?, ?> map, String key, Consumer<Boolean> setter) {
        Object val = map.get(key);
        if (val instanceof Boolean b) setter.accept(b);
    }
}
