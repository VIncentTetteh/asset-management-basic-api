package com.example.demo.repositories;

import com.example.demo.models.NotificationPreferences;
import com.example.demo.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, UUID> {

    Optional<NotificationPreferences> findByUser(User user);
}
