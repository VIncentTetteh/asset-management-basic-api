package com.assetiq.repositories;

import com.assetiq.models.NotificationPreferences;
import com.assetiq.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, UUID> {

    Optional<NotificationPreferences> findByUser(User user);
}
