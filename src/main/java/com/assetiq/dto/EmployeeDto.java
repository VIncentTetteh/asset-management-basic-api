package com.assetiq.dto;

import com.assetiq.enums.EmployeeStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmployeeDto {
    private UUID id;

    @Size(max = 100)
    private String employeeNumber;

    @NotBlank
    @Size(max = 255)
    private String firstName;

    @NotBlank
    @Size(max = 255)
    private String lastName;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 100)
    private String phone;

    @Size(max = 255)
    private String jobTitle;

    private UUID departmentId;
    private String departmentName;
    private UUID managerId;
    private String managerName;
    private UUID userId;
    private EmployeeStatus status;
    private LocalDate hireDate;
    private LocalDate terminationDate;
    private String notes;
    private UUID organisationId;

    /** Read-only convenience for list views: number of ACTIVE checkouts held. */
    private Long activeAssetCount;
}
