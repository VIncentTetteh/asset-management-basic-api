package com.assetiq.repositories;

import com.assetiq.models.Role;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByNameAndOrganisationId(String name, UUID organisationId);

    Set<Role> findByOrganisationId(UUID organisationId);

    // Tenant + soft-delete scoped
    Optional<Role> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Set<Role> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<Role> findByNameAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);

    /** True when any live user holds the role as primary role or through user_roles. */
    @org.springframework.data.jpa.repository.Query("""
            select count(u) > 0 from User u left join u.roles r
            where u.deletedAt is null and (u.role.id = :roleId or r.id = :roleId)
            """)
    boolean isAssignedToAnyUser(@org.springframework.data.repository.query.Param("roleId") java.util.UUID roleId);
}
