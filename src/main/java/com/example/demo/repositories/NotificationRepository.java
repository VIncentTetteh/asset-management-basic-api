package com.example.demo.repositories;

import com.example.demo.enums.NotificationType;
import com.example.demo.models.Notification;
import com.example.demo.models.Organisation;
import com.example.demo.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserAndOrganisationAndDeletedAtIsNull(User user, Organisation org, Pageable pageable);

    Page<Notification> findByUserAndOrganisationAndReadAndDeletedAtIsNull(User user, Organisation org, boolean read, Pageable pageable);

    Page<Notification> findByUserAndOrganisationAndTypeAndDeletedAtIsNull(User user, Organisation org, NotificationType type, Pageable pageable);

    Page<Notification> findByUserAndOrganisationAndTypeAndReadAndDeletedAtIsNull(User user, Organisation org, NotificationType type, boolean read, Pageable pageable);

    long countByUserAndOrganisationAndReadAndDeletedAtIsNull(User user, Organisation org, boolean read);

    long countByUserAndOrganisationAndDeletedAtIsNull(User user, Organisation org);

    Optional<Notification> findByIdAndUserAndOrganisationAndDeletedAtIsNull(UUID id, User user, Organisation org);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.user = :user AND n.organisation = :org AND n.read = false AND n.deletedAt IS NULL")
    int markAllReadByUserAndOrganisation(@Param("user") User user, @Param("org") Organisation org, @Param("readAt") java.time.Instant readAt);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.organisation = :org AND n.type = :type AND n.deletedAt IS NULL")
    long countByUserAndOrganisationAndType(@Param("user") User user, @Param("org") Organisation org, @Param("type") NotificationType type);
}
