package com.assetiq.services;

import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Database-backed global session invalidation independent of Redis availability. */
@Service
@Transactional
public class SessionRevocationService {

    private final UserRepository userRepository;
    private final RefreshSessionService refreshSessionService;

    public SessionRevocationService(UserRepository userRepository,
                                    RefreshSessionService refreshSessionService) {
        this.userRepository = userRepository;
        this.refreshSessionService = refreshSessionService;
    }

    public void revokeAll(User user) {
        user.setSessionVersion(user.getSessionVersion() + 1);
        userRepository.save(user);
        refreshSessionService.revokeAll(user);
    }
}
