package com.assetiq.services.impl;

import com.assetiq.dto.DepartmentDto;
import com.assetiq.models.Department;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepartmentServiceImplRenameTest {

    @Mock DepartmentRepository departmentRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock UserRepository userRepository;

    private DepartmentServiceImpl service;
    private Organisation org;
    private Department dept;

    @BeforeEach
    void setUp() {
        service = new DepartmentServiceImpl(departmentRepository, organisationRepository, userRepository);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        dept = new Department();
        dept.setId(UUID.randomUUID());
        dept.setOrganisation(org);
        dept.setName("finance");
        when(departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(dept.getId(), org)).thenReturn(Optional.of(dept));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private DepartmentDto rename(String name) {
        DepartmentDto dto = new DepartmentDto();
        dto.setName(name);
        return dto;
    }

    @Test
    void clearBudgetLimitRemovesThePlanningCap() {
        dept.setBudgetLimit(new java.math.BigDecimal("50000.00"));
        DepartmentDto dto = new DepartmentDto();
        dto.setClearBudgetLimit(true);

        service.update(dept.getId(), dto);

        assertThat(dept.getBudgetLimit()).isNull();
    }

    @Test
    void anOmittedBudgetLimitLeavesTheCapAlone() {
        dept.setBudgetLimit(new java.math.BigDecimal("50000.00"));

        service.update(dept.getId(), new DepartmentDto());

        assertThat(dept.getBudgetLimit()).isEqualByComparingTo("50000.00");
    }

    @Test
    void caseOnlyRenameIsSaved() {
        service.update(dept.getId(), rename("Finance"));

        assertThat(dept.getName()).isEqualTo("Finance");
    }

    @Test
    void renameIsTrimmed() {
        service.update(dept.getId(), rename("  Finance & Admin  "));

        assertThat(dept.getName()).isEqualTo("Finance & Admin");
    }

    @Test
    void duplicateOfAnotherDepartmentIsRefused() {
        when(departmentRepository.existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNullAndIdNot(
                eq("Operations"), eq(org), eq(dept.getId()))).thenReturn(true);

        assertThatThrownBy(() -> service.update(dept.getId(), rename("Operations")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(dept.getName()).isEqualTo("finance");
    }

    @Test
    void blankNameIsRefused() {
        assertThatThrownBy(() -> service.update(dept.getId(), rename("   ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyDescriptionClearsIt() {
        dept.setDescription("Money matters");
        DepartmentDto dto = new DepartmentDto();
        dto.setDescription("");

        service.update(dept.getId(), dto);

        assertThat(dept.getDescription()).isNull();
    }

    @Test
    void descriptionIsSavedTrimmed() {
        DepartmentDto dto = new DepartmentDto();
        dto.setDescription("  Money matters ");

        service.update(dept.getId(), dto);

        assertThat(dept.getDescription()).isEqualTo("Money matters");
    }
}
