package com.assetiq.models;

import com.assetiq.enums.ChecklistItemType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A single step on an onboarding/offboarding checklist. ASSET_ISSUE items
 * reference the asset to hand over; ASSET_RETURN items reference the active
 * {@link CheckoutRecord} that completing the item checks in.
 */
@Entity
@Table(name = "employee_checklist_item")
@Getter
@Setter
public class EmployeeChecklistItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    private EmployeeChecklist checklist;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "item_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChecklistItemType itemType = ChecklistItemType.GENERAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_record_id")
    private CheckoutRecord checkoutRecord;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean completed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_id")
    private User completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;
}
