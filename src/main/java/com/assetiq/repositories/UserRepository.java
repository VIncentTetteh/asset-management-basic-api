package com.assetiq.repositories;

import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // ── Auth-path queries — eagerly join role, organisation, department ────────
    // open-in-view=false means the Hibernate session is closed before the
    // controller runs. These three associations are accessed in login, token
    // refresh, and the JWT filter, so they must be loaded in the same query or
    // accessing them later will throw LazyInitializationException.

    @EntityGraph(attributePaths = {"role", "organisation", "department"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"role", "organisation", "department"})
    Optional<User> findByEmailAndOrganisationId(String email, UUID organisationId);

    @Override
    @EntityGraph(attributePaths = {"role", "organisation", "department"})
    Optional<User> findById(UUID id);

    @EntityGraph(attributePaths = {"role", "organisation", "department"})
    List<User> findAllByEmail(String email);

    // ── Other queries (role/org/dept not accessed outside a transaction) ──────

    Optional<User> findByEmployeeIdAndDeletedAtIsNull(String employeeId);

    Set<User> findByOrganisationId(UUID organisationId);

    Set<User> findByDepartmentId(UUID departmentId);

    Set<User> findByRoleId(UUID roleId);

    List<User> findAllByCreatedByAndDeletedAtIsNull(String createdBy);

    Optional<User> findByIdAndOrganisation(UUID id, com.assetiq.models.Organisation organisation);

    Optional<User> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    List<User> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<User> findByResetPasswordToken(String resetPasswordToken);

    long countByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<User> findByEmployeeId(String userEmployeeId);

    List<User> findByOrganisationAndRole_NameContainingIgnoreCaseAndDeletedAtIsNull(Organisation org, String roleName);

}
