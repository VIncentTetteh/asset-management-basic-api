package com.assetiq.dto;

import com.assetiq.enums.ChecklistStatus;
import com.assetiq.enums.ChecklistType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmployeeChecklistDto {
    private UUID id;
    private UUID employeeId;
    private ChecklistType checklistType;
    private ChecklistStatus status;
    private Instant completedAt;
    private Instant createdAt;
    private List<EmployeeChecklistItemDto> items;
}
