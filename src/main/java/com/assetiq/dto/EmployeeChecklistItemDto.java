package com.assetiq.dto;

import com.assetiq.enums.ChecklistItemType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmployeeChecklistItemDto {
    private UUID id;
    private UUID checklistId;

    @NotBlank
    @Size(max = 500)
    private String title;

    private ChecklistItemType itemType;
    private UUID assetId;
    private String assetName;
    private UUID checkoutRecordId;
    private int sortOrder;
    private boolean completed;
    private UUID completedById;
    private String completedByName;
    private Instant completedAt;
}
