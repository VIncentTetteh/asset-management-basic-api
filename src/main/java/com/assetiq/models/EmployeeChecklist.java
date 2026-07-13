package com.assetiq.models;

import com.assetiq.enums.ChecklistStatus;
import com.assetiq.enums.ChecklistType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employee_checklist")
@Getter
@Setter
public class EmployeeChecklist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "checklist_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChecklistType checklistType;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChecklistStatus status = ChecklistStatus.OPEN;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<EmployeeChecklistItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
