package com.example.demo.models;

import com.example.demo.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;



import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "email", "organisation_id" }, name = "uk_user_email_per_org"),
        @UniqueConstraint(columnNames = { "employee_id", "organisation_id" }, name = "uk_user_employeeid_per_org")
})
@Data
@EqualsAndHashCode(callSuper = true)
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

    @Column(nullable = false)
    private String employeeId;

    private String jobTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "reset_password_token", length = 100)
    private String resetPasswordToken;

    @Column(name = "reset_password_token_expiry")
    private Instant resetPasswordTokenExpiry;

    @ManyToOne(fetch = FetchType.LAZY)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    @OneToMany(mappedBy = "assignedUser")
    private Set<Asset> assignedAssets;

}
