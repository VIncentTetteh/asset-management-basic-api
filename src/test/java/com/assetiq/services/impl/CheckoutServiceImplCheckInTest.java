package com.assetiq.services.impl;

import com.assetiq.dto.CheckoutRecordDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.CheckoutStatus;
import com.assetiq.models.*;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.services.AssetStateTransitionService;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Check-in keeps the return notes and names the real holder. */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplCheckInTest {

    @Mock private CheckoutRecordRepository checkoutRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private NotificationService notificationService;
    @Mock private AssetStateTransitionService stateTransitionService;

    @InjectMocks
    private CheckoutServiceImpl checkoutService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void appendReturnNotes_keepsCheckoutNotesAndAddsReturnLine() {
        assertThat(CheckoutServiceImpl.appendReturnNotes("Charger included", " Screen cracked "))
                .isEqualTo("Charger included\nReturn notes: Screen cracked");
        assertThat(CheckoutServiceImpl.appendReturnNotes(null, "Fine")).isEqualTo("Return notes: Fine");
        assertThat(CheckoutServiceImpl.appendReturnNotes("Keep", "  ")).isEqualTo("Keep");
    }

    @Test
    void checkIn_storesReturnNotesAndNotifiesWithEmployeeHolder() {
        Organisation org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        lenient().when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));

        User actor = new User();
        actor.setId(UUID.randomUUID());
        actor.setEmail("clerk@example.com");
        actor.setFirstName("Front");
        actor.setLastName("Desk");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getEmail(), "n/a", List.of()));
        when(userRepository.findByEmailAndOrganisationId(actor.getEmail(), org.getId())).thenReturn(Optional.of(actor));

        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setFirstName("Ama");
        employee.setLastName("Mensah");

        Asset asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setName("Laptop");
        asset.setStatus(AssetStatus.IN_USE);

        CheckoutRecord record = new CheckoutRecord();
        record.setId(UUID.randomUUID());
        record.setAsset(asset);
        record.setCheckedOutBy(actor);
        record.setEmployee(employee);
        record.setOrganisation(org);
        record.setStatus(CheckoutStatus.ACTIVE);
        record.setNotes("Charger included");
        when(checkoutRepository.findById(record.getId())).thenReturn(Optional.of(record));
        when(checkoutRepository.save(any(CheckoutRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRecordDto in = new CheckoutRecordDto();
        in.setConditionOnReturn("Damaged");
        in.setNotes("Screen cracked");
        CheckoutRecordDto out = checkoutService.checkIn(record.getId(), in);

        assertThat(out.getStatus()).isEqualTo(CheckoutStatus.RETURNED);
        assertThat(out.getConditionOnReturn()).isEqualTo("Damaged");
        assertThat(out.getCheckedInByName()).isEqualTo("Front Desk");
        assertThat(out.getNotes()).isEqualTo("Charger included\nReturn notes: Screen cracked");
        verify(notificationService).notifyOrgAdmins(eq(org), any(), anyString(), contains("Ama Mensah"), any(), any());
    }

    @Test
    void overdueIsReadableByEveryoneWhoCanReadTheList() throws Exception {
        java.lang.reflect.Method list = com.assetiq.controllers.v1.CheckoutController.class.getMethod("listByOrg");
        java.lang.reflect.Method overdue = null;
        for (java.lang.reflect.Method m : com.assetiq.controllers.v1.CheckoutController.class.getMethods()) {
            org.springframework.web.bind.annotation.GetMapping get =
                    m.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
            if (get != null && java.util.Arrays.asList(get.value()).contains("/overdue")) overdue = m;
        }
        assertThat(overdue).isNotNull();
        assertThat(overdue.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value())
                .isEqualTo(list.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value());
    }
}
