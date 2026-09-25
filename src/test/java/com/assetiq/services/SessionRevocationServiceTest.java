package com.assetiq.services;

import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionRevocationServiceTest {

    @Test
    void globalRevokeChangesDatabaseVersionAndRevokesRefreshSessions() {
        UserRepository users = mock(UserRepository.class);
        RefreshSessionService refreshSessions = mock(RefreshSessionService.class);
        SessionRevocationService service = new SessionRevocationService(users, refreshSessions);
        User user = new User();
        user.setSessionVersion(7);

        service.revokeAll(user);

        assertThat(user.getSessionVersion()).isEqualTo(8);
        verify(users).save(user);
        verify(refreshSessions).revokeAll(user);
    }
}
