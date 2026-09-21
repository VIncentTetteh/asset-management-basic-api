package com.assetiq.services;

import com.assetiq.enums.OrganisationStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.RefreshSession;
import com.assetiq.models.User;
import com.assetiq.repositories.RefreshSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshSessionServiceTest {

    @Test
    void issueStoresOnlyAHash() {
        RefreshSessionRepository repository = mock(RefreshSessionRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RefreshSessionService service = new RefreshSessionService(repository, Duration.ofHours(12));

        var issued = service.issue(activeUser());

        ArgumentCaptor<RefreshSession> capture = ArgumentCaptor.forClass(RefreshSession.class);
        verify(repository).save(capture.capture());
        assertThat(issued.token()).isNotBlank();
        assertThat(capture.getValue().getTokenHash()).hasSize(64).doesNotContain(issued.token());
        assertThat(capture.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void reuseOfRotatedTokenRevokesItsWholeFamily() {
        RefreshSessionRepository repository = mock(RefreshSessionRepository.class);
        RefreshSession reused = activeSession(activeUser());
        reused.setRevokedAt(Instant.now().minusSeconds(1));
        RefreshSession sibling = activeSession(reused.getUser());
        sibling.setFamilyId(reused.getFamilyId());
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(reused));
        when(repository.findByFamilyIdAndRevokedAtIsNull(reused.getFamilyId())).thenReturn(List.of(sibling));
        RefreshSessionService service = new RefreshSessionService(repository, Duration.ofHours(12));

        assertThatThrownBy(() -> service.rotate("already-used-token"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("reuse detected");
        assertThat(sibling.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeAllRevokesEveryActiveDeviceSession() {
        RefreshSessionRepository repository = mock(RefreshSessionRepository.class);
        User user = activeUser();
        RefreshSession phone = activeSession(user);
        RefreshSession desktop = activeSession(user);
        when(repository.findByUserIdAndRevokedAtIsNull(user.getId()))
                .thenReturn(List.of(phone, desktop));
        RefreshSessionService service = new RefreshSessionService(repository, Duration.ofHours(12));

        service.revokeAll(user);

        assertThat(phone.getRevokedAt()).isNotNull();
        assertThat(desktop.getRevokedAt()).isNotNull();
        verify(repository).saveAll(List.of(phone, desktop));
    }

    private static RefreshSession activeSession(User user) {
        RefreshSession session = new RefreshSession();
        session.setTokenHash("a".repeat(64));
        session.setFamilyId(UUID.randomUUID());
        session.setUser(user);
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        return session;
    }

    private static User activeUser() {
        Organisation org = new Organisation();
        org.setStatus(OrganisationStatus.ACTIVE);
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        user.setOrganisation(org);
        return user;
    }
}
