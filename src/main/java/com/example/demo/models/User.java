package com.example.demo.models;

import com.example.demo.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "app_user", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"email", "organisation_id"}, name = "uk_user_email_per_org")
})
public class User extends BaseEntity {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(nullable = false)
    private String passwordHash;

    @Column(unique = true)
    private String employeeId;

    private String jobTitle;

    @ManyToOne
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToOne
    private Organisation organisation;

    @ManyToOne
    private Department department;

    @OneToMany(mappedBy = "assignedUser")
    private Set<Asset> assignedAssets;

}

