package com.assetiq.repositories;

import com.assetiq.enums.EmployeeStatus;
import com.assetiq.models.Employee;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    /** Full roster for a tenant — used by the account data export. */
    List<Employee> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<Employee> findByUserAndOrganisationAndDeletedAtIsNull(User user, Organisation organisation);

    Optional<Employee> findByOrganisationAndEmployeeNumberIgnoreCaseAndDeletedAtIsNull(
            Organisation organisation, String employeeNumber);

    @Query("""
            SELECT e FROM Employee e
            WHERE e.organisation = :org
              AND e.deletedAt IS NULL
              AND (:departmentId IS NULL OR e.department.id = :departmentId)
              AND (:status IS NULL OR e.status = :status)
              AND (:q IS NULL
                   OR LOWER(e.firstName)      LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(e.lastName)       LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(e.email)          LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(e.employeeNumber) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    Page<Employee> search(@Param("org") Organisation org,
                          @Param("departmentId") UUID departmentId,
                          @Param("status") EmployeeStatus status,
                          @Param("q") String q,
                          Pageable pageable);
}
