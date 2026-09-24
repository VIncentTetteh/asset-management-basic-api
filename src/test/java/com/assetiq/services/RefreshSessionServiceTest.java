package com.assetiq.services;

import com.assetiq.enums.OrganisationStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.RefreshSession;
import com.assetiq.models.User;
import com.assetiq.repositories.RefreshSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshSessionServiceTest {

    private static final Duration IDLE = Duration.ofDays(30);
    private static final Duration ABSOLUTE = Duration.ofDays(90);
    private static final Duration GRACE = Duration.ofSeconds(30);

    @Test
    void issueStoresOnlyAHash() {
        RefreshSessionRepository repository = mock(RefreshSessionRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RefreshSessionService service = service(repository);

        var issued = service.issue(activeUser());

        ArgumentCaptor<RefreshSession> capture = ArgumentCaptor.forClass(RefreshSession.class);
        verify(repository).save(capture.capture());
        assertThat(issued.token()).isNotBlank();
        assertThat(capture.getValue().getTokenHash()).hasSize(64).doesNotContain(issued.token());
        assertThat(capture.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("nothing persisted lets the server reconstruct a refresh token on its own")
    void noPersistedFieldLeaksTheToken() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository());
        User user = activeUser();

        String first = service.issue(user).token();
        String second = service.rotate(first).token();

        for (RefreshSession row : store.sessions()) {
            assertThat(row.getTokenHash()).doesNotContain(first).doesNotContain(second);
            if (row.getReplacementEnvelope() != null) {
                assertThat(row.getReplacementEnvelope()).doesNotContain(second);
            }
        }
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
        RefreshSessionService service = service(repository);

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
        RefreshSessionService service = service(repository);

        service.revokeAll(user);

        assertThat(phone.getRevokedAt()).isNotNull();
        assertThat(desktop.getRevokedAt()).isNotNull();
        verify(repository).saveAll(List.of(phone, desktop));
    }

    // ── The replay grace window ──────────────────────────────────────────────

    @Test
    @DisplayName("a token replayed inside the window returns the same replacement, not a new one")
    void replayInsideTheWindowReturnsTheSameReplacement() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository());
        User user = activeUser();

        String first = service.issue(user).token();
        var rotated = service.rotate(first);
        int rowsAfterRotation = store.sessions().size();

        var replayed = service.rotate(first);

        assertThat(replayed.token()).isEqualTo(rotated.token());
        assertThat(replayed.expiresAt()).isEqualTo(rotated.expiresAt());
        // The family did not fork: no third token was minted.
        assertThat(store.sessions()).hasSize(rowsAfterRotation);
        assertThat(store.activeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the replacement stays usable after a replay")
    void replayLeavesTheReplacementUsable() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository());

        String first = service.issue(activeUser()).token();
        String second = service.rotate(first).token();
        service.rotate(first);

        assertThat(service.rotate(second).token()).isNotBlank();
    }

    @Test
    @DisplayName("outside the window the same replay is reuse and revokes the family")
    void replayOutsideTheWindowRevokesTheFamily() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository(), GRACE);
        User user = activeUser();

        String first = service.issue(user).token();
        service.rotate(first);
        // Wind the consumption back past the window.
        store.sessions().stream()
                .filter(s -> s.getConsumedAt() != null)
                .forEach(s -> s.setConsumedAt(Instant.now().minus(GRACE).minusSeconds(5)));

        assertThatThrownBy(() -> service.rotate(first))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("reuse detected");
        assertThat(store.activeCount()).isZero();
    }

    @Test
    @DisplayName("a grace of zero disables the window entirely")
    void graceOfZeroKeepsTheOldBehaviour() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository(), Duration.ZERO);

        String first = service.issue(activeUser()).token();
        service.rotate(first);

        assertThatThrownBy(() -> service.rotate(first))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("reuse detected");
        assertThat(store.activeCount()).isZero();
    }

    @Test
    @DisplayName("once the family has moved on, a replay inside the window is still reuse")
    void replayAfterTheFamilyMovedOnRevokes() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository());

        String first = service.issue(activeUser()).token();
        String second = service.rotate(first).token();
        service.rotate(second); // the family head is now the third token

        assertThatThrownBy(() -> service.rotate(first))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("reuse detected");
        assertThat(store.activeCount()).isZero();
    }

    @Test
    @DisplayName("a token revoked by logout never replays, however recently")
    void logoutDefeatsTheGraceWindow() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository());

        String first = service.issue(activeUser()).token();
        String second = service.rotate(first).token();
        service.revoke(second); // logout

        assertThatThrownBy(() -> service.rotate(first))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("reuse detected");
        assertThatThrownBy(() -> service.rotate(second))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(store.activeCount()).isZero();
    }

    @Test
    @DisplayName("a password change revokes the family and defeats the grace window")
    void revokeAllDefeatsTheGraceWindow() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository());
        User user = activeUser();

        String first = service.issue(user).token();
        service.rotate(first);
        service.revokeAll(user); // what SessionRevocationService does on password change

        assertThatThrownBy(() -> service.rotate(first))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("reuse detected");
        assertThat(store.activeCount()).isZero();
    }

    // ── Idle timeout and absolute cap ────────────────────────────────────────

    @Test
    @DisplayName("a family unused for longer than the idle timeout is dead")
    void idleTimeoutEndsAnUnusedFamily() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository());

        String first = service.issue(activeUser()).token();
        store.sessions().forEach(s -> s.setExpiresAt(Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> service.rotate(first))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("rotation resets the idle clock but never extends the absolute cap")
    void rotationExtendsIdleButNotTheCap() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository());

        String first = service.issue(activeUser()).token();
        Instant cap = store.sessions().get(0).getFamilyExpiresAt();
        Instant firstExpiry = store.sessions().get(0).getExpiresAt();

        service.rotate(first);
        RefreshSession head = store.head();

        assertThat(head.getFamilyExpiresAt()).isEqualTo(cap);
        assertThat(head.getExpiresAt()).isAfterOrEqualTo(firstExpiry);
        assertThat(head.getExpiresAt()).isBeforeOrEqualTo(cap);
    }

    @Test
    @DisplayName("the absolute cap ends the family no matter how active the device has been")
    void absoluteCapEndsAnActiveFamily() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository());

        String token = service.issue(activeUser()).token();
        for (int i = 0; i < 3; i++) token = service.rotate(token).token();

        // The cap falls due. The idle clock is nowhere near.
        Instant lapsed = Instant.now().minusSeconds(1);
        store.sessions().forEach(s -> s.setFamilyExpiresAt(lapsed));

        String presented = token;
        assertThatThrownBy(() -> service.rotate(presented))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("expired");
        assertThat(store.activeCount()).isZero();
    }

    @Test
    @DisplayName("an idle window longer than the cap cannot outlive it")
    void tokenNeverOutlivesTheCap() {
        Store store = new Store();
        RefreshSessionService service = new RefreshSessionService(
                store.repository(), Duration.ofDays(30), Duration.ofDays(2), GRACE);

        service.issue(activeUser());

        RefreshSession row = store.sessions().get(0);
        assertThat(row.getExpiresAt()).isBeforeOrEqualTo(row.getFamilyExpiresAt());
        assertThat(row.getExpiresAt()).isBefore(Instant.now().plus(Duration.ofDays(3)));
    }

    @Test
    @DisplayName("legacy rows with no recorded cap still rotate")
    void legacyRowsWithoutACapStillRotate() {
        Store store = new Store();
        RefreshSessionService service = service(store.repository());

        String first = service.issue(activeUser()).token();
        store.sessions().forEach(s -> s.setFamilyExpiresAt(null));

        assertThat(service.rotate(first).token()).isNotBlank();
        assertThat(store.head().getFamilyExpiresAt()).isNotNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static RefreshSessionService service(RefreshSessionRepository repository) {
        return service(repository, GRACE);
    }

    private static RefreshSessionService service(RefreshSessionRepository repository, Duration grace) {
        return new RefreshSessionService(repository, IDLE, ABSOLUTE, grace);
    }

    /** A functional stand-in for the repository: the service hashes its own tokens. */
    private static final class Store {
        private final Map<String, RefreshSession> byHash = new LinkedHashMap<>();
        private final RefreshSessionRepository repository = mock(RefreshSessionRepository.class);

        Store() {
            when(repository.save(any())).thenAnswer(invocation -> {
                RefreshSession session = invocation.getArgument(0);
                byHash.put(session.getTokenHash(), session);
                return session;
            });
            when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(repository.findByTokenHash(anyString()))
                    .thenAnswer(invocation -> Optional.ofNullable(byHash.get(invocation.getArgument(0))));
            when(repository.findByFamilyIdAndRevokedAtIsNull(any())).thenAnswer(invocation -> {
                UUID familyId = invocation.getArgument(0);
                List<RefreshSession> matches = new ArrayList<>();
                for (RefreshSession session : byHash.values()) {
                    if (familyId.equals(session.getFamilyId()) && session.getRevokedAt() == null) {
                        matches.add(session);
                    }
                }
                return matches;
            });
            when(repository.findByUserIdAndRevokedAtIsNull(any())).thenAnswer(invocation -> {
                UUID userId = invocation.getArgument(0);
                List<RefreshSession> matches = new ArrayList<>();
                for (RefreshSession session : byHash.values()) {
                    if (session.getRevokedAt() == null && session.getUser() != null
                            && userId.equals(session.getUser().getId())) {
                        matches.add(session);
                    }
                }
                return matches;
            });
        }

        RefreshSessionRepository repository() {
            return repository;
        }

        List<RefreshSession> sessions() {
            return new ArrayList<>(byHash.values());
        }

        RefreshSession head() {
            return sessions().stream().filter(s -> s.getRevokedAt() == null).findFirst().orElseThrow();
        }

        long activeCount() {
            return sessions().stream().filter(s -> s.getRevokedAt() == null).count();
        }
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
