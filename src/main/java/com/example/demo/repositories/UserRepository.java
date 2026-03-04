package com.example.demo.repositories;

import com.example.demo.models.Organisation;
import com.example.demo.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndOrganisationId(String email, UUID organisationId);

    Optional<User> findByEmployeeId(String employeeId);

    Set<User> findByOrganisationId(UUID organisationId);

    Set<User> findByDepartmentId(UUID departmentId);

    Set<User> findByRoleId(UUID roleId);

    List<User> findAllByCreatedByAndDeletedAtIsNull(String createdBy);

    Optional<User> findByIdAndOrganisation(UUID id, com.example.demo.models.Organisation organisation);

    Set<User> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<User> findByResetPasswordToken(String resetPasswordToken);
}
