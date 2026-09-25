package com.assetiq.services.impl;

import com.assetiq.enums.NotificationType;
import com.assetiq.models.Notification;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.NotificationPreferencesRepository;
import com.assetiq.repositories.NotificationRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPreferencesRepository preferencesRepository;
    @Mock UserRepository userRepository;
    @Mock EmailService emailService;

    private NotificationServiceImpl service;
    private Organisation organisation;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(
                notificationRepository, preferencesRepository, userRepository, emailService);
        organisation = new Organisation();
        organisation.setId(UUID.randomUUID());
        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setOrganisation(organisation);
    }

    @Test
    void notifyOrgAdminsOnce_skipsAnAlreadyDeliveredEvent() {
        String key = "lifecycle:warranty:asset-1:2026-09-23";
        when(userRepository.findByOrganisationAndRole_NameContainingIgnoreCaseAndDeletedAtIsNull(
                organisation, "ADMIN")).thenReturn(List.of(admin));
        when(notificationRepository.existsByUserAndOrganisationAndDeduplicationKey(
                admin, organisation, key)).thenReturn(false, true);

        service.notifyOrgAdminsOnce(organisation, NotificationType.WARRANTY_EXPIRY,
                "Warranty", "Message", UUID.randomUUID(), "/assets/1", key);
        service.notifyOrgAdminsOnce(organisation, NotificationType.WARRANTY_EXPIRY,
                "Warranty", "Message", UUID.randomUUID(), "/assets/1", key);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getDeduplicationKey()).isEqualTo(key);
        assertThat(captor.getValue().getOrganisation()).isSameAs(organisation);
        assertThat(captor.getValue().getUser()).isSameAs(admin);
        verifyNoInteractions(emailService);
    }

    @Test
    void notifyOrgAdminsOnce_rejectsBlankKeys() {
        assertThatThrownBy(() -> service.notifyOrgAdminsOnce(
                organisation, NotificationType.SYSTEM, "Title", "Message",
                null, null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deduplicationKey");
    }
}
