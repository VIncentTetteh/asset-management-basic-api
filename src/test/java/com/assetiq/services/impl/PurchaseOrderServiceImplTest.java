package com.assetiq.services.impl;

import com.assetiq.dto.PurchaseOrderDto;
import com.assetiq.enums.POStatus;
import com.assetiq.models.*;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceImplTest {

    @Mock PurchaseOrderRepository poRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock UserRepository userRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock NotificationService notificationService;
    @Mock CurrencyResolver currencyResolver;

    private PurchaseOrderServiceImpl service;
    private Organisation organisation;
    private User maker;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderServiceImpl(
                poRepository, organisationRepository, departmentRepository,
                supplierRepository, userRepository, budgetRepository,
                notificationService, currencyResolver);
        organisation = new Organisation();
        organisation.setId(UUID.randomUUID());
        maker = user("maker@example.com");
        TenantContext.setOrganisationId(organisation.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(organisation.getId()))
                .thenReturn(Optional.of(organisation));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_ignoresClientApprovalStateAndRecordsAuthenticatedMaker() {
        Department department = new Department();
        department.setId(UUID.randomUUID());
        Supplier supplier = new Supplier();
        supplier.setId(UUID.randomUUID());
        supplier.setName("Network Vendor");
        authenticate(maker);

        PurchaseOrderDto request = new PurchaseOrderDto();
        request.setPoNumber("PO-1");
        request.setTotalAmount(new BigDecimal("100.00"));
        request.setCurrency("GHS");
        request.setStatus(POStatus.APPROVED);
        request.setDepartmentId(department.getId());
        request.setSupplierId(supplier.getId());

        when(departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(department.getId(), organisation))
                .thenReturn(Optional.of(department));
        when(supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(supplier.getId(), organisation))
                .thenReturn(Optional.of(supplier));
        when(userRepository.findByEmailAndOrganisationId(maker.getEmail(), organisation.getId()))
                .thenReturn(Optional.of(maker));
        when(currencyResolver.resolveOrDefault("GHS")).thenReturn("GHS");
        when(poRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder po = invocation.getArgument(0);
            po.setId(UUID.randomUUID());
            return po;
        });

        PurchaseOrderDto result = service.createPurchaseOrder(request);

        assertThat(result.getStatus()).isEqualTo(POStatus.DRAFT);
        assertThat(result.getRequestedById()).isEqualTo(maker.getId());
    }

    @Test
    void approve_rejectsSelfApproval() {
        PurchaseOrder po = purchaseOrderRequestedBy(maker);
        authenticate(maker);
        when(poRepository.findByIdAndOrganisationAndDeletedAtIsNull(po.getId(), organisation))
                .thenReturn(Optional.of(po));
        when(userRepository.findByEmailAndOrganisationId(maker.getEmail(), organisation.getId()))
                .thenReturn(Optional.of(maker));

        assertThatThrownBy(() -> service.approvePurchaseOrder(po.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot approve");
        verify(poRepository, never()).save(any());
    }

    @Test
    void approve_rejectsCrossCurrencyBudgetMutation() {
        User checker = user("checker@example.com");
        PurchaseOrder po = purchaseOrderRequestedBy(maker);
        Budget budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setCurrency("USD");
        budget.setSpentAmount(BigDecimal.ZERO);
        po.setCurrency("GHS");
        po.setTotalAmount(new BigDecimal("50.00"));
        po.setLinkedBudget(budget);
        authenticate(checker);

        when(poRepository.findByIdAndOrganisationAndDeletedAtIsNull(po.getId(), organisation))
                .thenReturn(Optional.of(po));
        when(userRepository.findByEmailAndOrganisationId(checker.getEmail(), organisation.getId()))
                .thenReturn(Optional.of(checker));

        assertThatThrownBy(() -> service.approvePurchaseOrder(po.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currencies must match");
        verify(budgetRepository, never()).save(any());
        verify(poRepository, never()).save(any());
    }

    private User user(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setOrganisation(organisation);
        return user;
    }

    private PurchaseOrder purchaseOrderRequestedBy(User requester) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(UUID.randomUUID());
        po.setOrganisation(organisation);
        po.setStatus(POStatus.DRAFT);
        po.setRequestedBy(requester);
        return po;
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        user.getEmail(), "n/a", java.util.List.of()));
    }
}
