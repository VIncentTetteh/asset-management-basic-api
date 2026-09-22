package com.assetiq.services.impl;

import com.assetiq.dto.AssetAuditDto;
import com.assetiq.enums.AuditStatus;
import com.assetiq.models.AssetAudit;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetAuditRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Audits: organisation-wide scope, default auditor and a status state machine")
class AuditServiceImplTest {

    @Mock AssetAuditRepository auditRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock UserRepository userRepository;

    private AuditServiceImpl service;
    private Organisation org;
    private User me;

    @BeforeEach
    void setUp() {
        service = new AuditServiceImpl(auditRepository, organisationRepository, departmentRepository, userRepository);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        me = new User();
        me.setId(UUID.randomUUID());
        me.setEmail("auditor@example.com");
        when(userRepository.findByEmailAndOrganisationId(me.getEmail(), org.getId())).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(me.getEmail(), "n/a", List.of()));
        when(auditRepository.save(any(AssetAudit.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void wholeOrganisationAuditDefaultsToTheCallerAndPlanned() {
        AssetAuditDto dto = new AssetAuditDto();
        dto.setAuditDate(LocalDate.of(2026, 10, 1));
        AssetAuditDto out = service.createAudit(dto);
        assertThat(out.getDepartmentId()).isNull();
        assertThat(out.getConductedById()).isEqualTo(me.getId());
        assertThat(out.getStatus()).isEqualTo(AuditStatus.PLANNED);
        assertThat(out.getOrganisationId()).isEqualTo(org.getId());
    }

    private AssetAudit audit(AuditStatus status) {
        AssetAudit a = new AssetAudit();
        a.setId(UUID.randomUUID());
        a.setOrganisation(org);
        a.setConductedBy(me);
        a.setStatus(status);
        when(auditRepository.findByIdAndOrganisationAndDeletedAtIsNull(a.getId(), org)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void cancelledIsAcceptedAndFinal() {
        AssetAudit a = audit(AuditStatus.PLANNED);
        assertThat(service.updateAuditStatus(a.getId(), "CANCELLED").getStatus()).isEqualTo(AuditStatus.CANCELLED);
        assertThatThrownBy(() -> service.updateAuditStatus(a.getId(), "IN_PROGRESS"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completedAuditCannotBeReopenedAndDiscrepancyMustBeResolved() {
        AssetAudit done = audit(AuditStatus.COMPLETED);
        assertThatThrownBy(() -> service.updateAuditStatus(done.getId(), "PLANNED"))
                .isInstanceOf(IllegalStateException.class);

        AssetAudit gap = audit(AuditStatus.DISCREPANCY_FOUND);
        assertThatThrownBy(() -> service.updateAuditStatus(gap.getId(), "COMPLETED"))
                .isInstanceOf(IllegalStateException.class);
        service.updateAuditStatus(gap.getId(), "RESOLVED");
        assertThat(service.updateAuditStatus(gap.getId(), "COMPLETED").getStatus()).isEqualTo(AuditStatus.COMPLETED);
    }

    @Test
    void unknownStatusIsABadRequest() {
        AssetAudit a = audit(AuditStatus.PLANNED);
        assertThatThrownBy(() -> service.updateAuditStatus(a.getId(), "DONE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAuditIsCreatedOnlyAsPlannedOrInProgress() {
        for (AuditStatus ok : List.of(AuditStatus.PLANNED, AuditStatus.IN_PROGRESS)) {
            AssetAuditDto dto = new AssetAuditDto();
            dto.setAuditDate(LocalDate.of(2026, 10, 1));
            dto.setStatus(ok);
            assertThat(service.createAudit(dto).getStatus()).isEqualTo(ok);
        }
        for (AuditStatus refused : List.of(AuditStatus.COMPLETED, AuditStatus.RESOLVED,
                AuditStatus.DISCREPANCY_FOUND, AuditStatus.CANCELLED)) {
            AssetAuditDto dto = new AssetAuditDto();
            dto.setAuditDate(LocalDate.of(2026, 10, 1));
            dto.setStatus(refused);
            assertThatThrownBy(() -> service.createAudit(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PLANNED or IN_PROGRESS");
        }
    }

    @Test
    void aLegacyAuditWithoutStatusReadsAsPlanned() {
        AssetAudit legacy = audit(null);
        assertThat(service.getAuditById(legacy.getId()).getStatus()).isEqualTo(AuditStatus.PLANNED);
    }
}
