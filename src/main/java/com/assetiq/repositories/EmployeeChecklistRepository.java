package com.assetiq.repositories;

import com.assetiq.enums.ChecklistStatus;
import com.assetiq.enums.ChecklistType;
import com.assetiq.models.Employee;
import com.assetiq.models.EmployeeChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeChecklistRepository extends JpaRepository<EmployeeChecklist, UUID> {

    List<EmployeeChecklist> findByEmployeeAndDeletedAtIsNullOrderByCreatedAtDesc(Employee employee);

    Optional<EmployeeChecklist> findFirstByEmployeeAndChecklistTypeAndStatusAndDeletedAtIsNull(
            Employee employee, ChecklistType checklistType, ChecklistStatus status);
}
