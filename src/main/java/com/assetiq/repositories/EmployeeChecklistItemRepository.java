package com.assetiq.repositories;

import com.assetiq.models.EmployeeChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmployeeChecklistItemRepository extends JpaRepository<EmployeeChecklistItem, UUID> {
}
