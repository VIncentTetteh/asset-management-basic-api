package com.assetiq.services.impl;

import com.assetiq.dto.PurchaseOrderDto;
import com.assetiq.enums.BudgetLedgerKind;
import com.assetiq.enums.BudgetStatus;
import com.assetiq.enums.POStatus;
import com.assetiq.models.*;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.NotificationService;
import com.assetiq.services.budget.BudgetPosting;
import com.assetiq.services.budget.LedgerFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The purchase-order state machine and its budget effects, run against a real
 * {@link com.assetiq.services.budget.BudgetLedgerService} over mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PurchaseOrderServiceImpl - workflow and budget")
class PurchaseOrderWorkflowTest {

    @Mock PurchaseOrderRepository poRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock UserRepository userRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock NotificationService notificationService;
    @Mock CurrencyResolver currencyResolver;

    private PurchaseOrderServiceImpl service;
    private LedgerFixture ledger;
    private Organisation org;
    private User maker;
    private User checker;
    private Budget budget;
    private PurchaseOrder po;

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixture(budgetRepository, notificationService);
        service = new PurchaseOrderServiceImpl(poRepository, organisationRepository, departmentRepository,
                supplierRepository, userRepository, budgetRepository, notificationService, currencyResolver,
                ledger.service);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        when(currencyResolver.resolveOrDefault(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(poRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        maker = user("maker@example.com");
        checker = user("checker@example.com");

        budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setOrganisation(org);
        budget.setName("IT");
        budget.setCurrency("GHS");
        budget.setTotalAmount(new BigDecimal("1000"));
        budget.setStatus(BudgetStatus.ACTIVE);
        LedgerFixture.lockable(budgetRepository, org, budget);
        when(budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(budget.getId(), org))
                .thenReturn(Optional.of(budget));

        Department department = new Department();
        department.setId(UUID.randomUUID());
        Supplier supplier = new Supplier();
        supplier.setId(UUID.randomUUID());
        supplier.setName("Vendor");

        po = new PurchaseOrder();
        po.setId(UUID.randomUUID());
        po.setOrganisation(org);
        po.setPoNumber("PO-1");
        po.setTotalAmount(new BigDecimal("400"));
        po.setCurrency("GHS");
        po.setStatus(POStatus.DRAFT);
        po.setDepartment(department);
        po.setSupplier(supplier);
        po.setLinkedBudget(budget);
        when(poRepository.findByIdAndOrganisationAndDeletedAtIsNull(po.getId(), org)).thenReturn(Optional.of(po));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("submit moves DRAFT to SUBMITTED and records the submitter as maker")
    void submit_setsMaker() {
        authenticate(maker);

        PurchaseOrderDto result = service.submitPurchaseOrder(po.getId());

        assertThat(result.getStatus()).isEqualTo(POStatus.SUBMITTED);
        assertThat(result.getRequestedById()).isEqualTo(maker.getId());
        assertThat(ledger.entries).isEmpty();
    }

    @Test
    @DisplayName("full happy path: approve commits, receive turns the commitment into spend")
    void approveThenReceive() {
        submitAs(maker);
        authenticate(checker);

        service.approvePurchaseOrder(po.getId());
        assertThat(po.getStatus()).isEqualTo(POStatus.APPROVED);
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("400");
        assertThat(budget.getSpentAmount()).isEqualByComparingTo("0");

        service.receivePurchaseOrder(po.getId());
        assertThat(po.getStatus()).isEqualTo(POStatus.DELIVERED);
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("0");
        assertThat(budget.getSpentAmount()).isEqualByComparingTo("400");
        assertThat(ledger.kinds()).containsExactly("PO_COMMIT", "PO_SPEND");
    }

    @Test
    @DisplayName("approval beyond available funds is refused and the order stays SUBMITTED")
    void approve_insufficientFunds() {
        budget.setSpentAmount(new BigDecimal("700"));
        submitAs(maker);
        authenticate(checker);

        assertThatThrownBy(() -> service.approvePurchaseOrder(po.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");
        assertThat(po.getStatus()).isEqualTo(POStatus.SUBMITTED);
        assertThat(po.getApprovedBy()).isNull();
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("cancelling an approved order releases its commitment")
    void cancelApproved_releases() {
        approve();

        service.cancelPurchaseOrder(po.getId());

        assertThat(po.getStatus()).isEqualTo(POStatus.CANCELLED);
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("0");
        assertThat(ledger.kinds()).containsExactly("PO_COMMIT", "PO_RELEASE");
    }

    @Test
    @DisplayName("deleting an approved order releases its commitment")
    void deleteApproved_releases() {
        approve();

        service.deletePurchaseOrder(po.getId());

        assertThat(po.getDeletedAt()).isNotNull();
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("0");
        assertThat(ledger.kinds()).containsExactly("PO_COMMIT", "PO_RELEASE");
    }

    @Test
    @DisplayName("deleting a delivered order reverses its spend")
    void deleteDelivered_reversesSpend() {
        approve();
        service.receivePurchaseOrder(po.getId());

        service.deletePurchaseOrder(po.getId());

        assertThat(budget.getSpentAmount()).isEqualByComparingTo("0");
        assertThat(ledger.kinds()).containsExactly("PO_COMMIT", "PO_SPEND", "PO_SPEND_REVERSAL");
    }

    @Test
    @DisplayName("a legacy order approved before commitments existed: receive moves nothing, delete reverses spend")
    void legacyApproved_noCommitmentEntry() {
        // Legacy approval charged spend directly and wrote no ledger entry.
        po.setStatus(POStatus.APPROVED);
        budget.setSpentAmount(new BigDecimal("400"));
        authenticate(checker);

        service.receivePurchaseOrder(po.getId());
        assertThat(budget.getSpentAmount()).isEqualByComparingTo("400");
        assertThat(ledger.entries).isEmpty();

        po.setStatus(POStatus.APPROVED); // and the cancel/delete route for one still open
        service.deletePurchaseOrder(po.getId());
        assertThat(budget.getSpentAmount()).isEqualByComparingTo("0");
        assertThat(ledger.kinds()).containsExactly("PO_SPEND_REVERSAL");
    }

    @Test
    @DisplayName("deleting a draft touches no budget")
    void deleteDraft_noBudgetEffect() {
        service.deletePurchaseOrder(po.getId());
        assertThat(ledger.entries).isEmpty();
        verify(budgetRepository, never()).findByIdForUpdate(any(), any());
    }

    @Test
    @DisplayName("an approved order cannot be rejected; cancel is the route")
    void rejectApproved_refused() {
        approve();
        assertThatThrownBy(() -> service.rejectPurchaseOrder(po.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancel");
    }

    @ParameterizedTest(name = "approve from {0} is illegal")
    @EnumSource(value = POStatus.class, names = { "DRAFT", "REJECTED", "DELIVERED", "CANCELLED" })
    void approve_illegalFrom(POStatus from) {
        po.setStatus(from);
        authenticate(checker);
        assertThatThrownBy(() -> service.approvePurchaseOrder(po.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(from.name());
        assertThat(ledger.entries).isEmpty();
    }

    @ParameterizedTest(name = "submit from {0} is illegal")
    @EnumSource(value = POStatus.class, names = { "APPROVED", "REJECTED", "DELIVERED", "CANCELLED" })
    void submit_illegalFrom(POStatus from) {
        po.setStatus(from);
        authenticate(maker);
        assertThatThrownBy(() -> service.submitPurchaseOrder(po.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "receive from {0} is illegal")
    @EnumSource(value = POStatus.class, names = { "DRAFT", "SUBMITTED", "REJECTED", "CANCELLED" })
    void receive_illegalFrom(POStatus from) {
        po.setStatus(from);
        assertThatThrownBy(() -> service.receivePurchaseOrder(po.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "cancel from {0} is illegal")
    @EnumSource(value = POStatus.class, names = { "REJECTED", "DELIVERED" })
    void cancel_illegalFrom(POStatus from) {
        po.setStatus(from);
        assertThatThrownBy(() -> service.cancelPurchaseOrder(po.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a submitted order can no longer be edited")
    void edit_onlyInDraft() {
        po.setStatus(POStatus.SUBMITTED);
        PurchaseOrderDto patch = new PurchaseOrderDto();
        patch.setTotalAmount(new BigDecimal("1"));
        assertThatThrownBy(() -> service.patchPurchaseOrder(po.getId(), patch))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("PUT links, relinks and unlinks the budget; a currency mismatch is refused")
    void update_linksBudget() {
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setPoNumber("PO-1");
        dto.setTotalAmount(new BigDecimal("400"));
        dto.setCurrency("GHS");
        dto.setDepartmentId(po.getDepartment().getId());
        dto.setSupplierId(po.getSupplier().getId());
        when(departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(po.getDepartment().getId(), org))
                .thenReturn(Optional.of(po.getDepartment()));
        when(supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(po.getSupplier().getId(), org))
                .thenReturn(Optional.of(po.getSupplier()));

        assertThat(service.updatePurchaseOrder(po.getId(), dto).getLinkedBudgetId()).isNull();

        dto.setLinkedBudgetId(budget.getId());
        assertThat(service.updatePurchaseOrder(po.getId(), dto).getLinkedBudgetId()).isEqualTo(budget.getId());

        dto.setCurrency("USD");
        assertThatThrownBy(() -> service.updatePurchaseOrder(po.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currencies must match");
    }

    @Test
    @DisplayName("an unknown linked budget is refused rather than silently dropped")
    void patch_unknownBudget() {
        PurchaseOrderDto patch = new PurchaseOrderDto();
        patch.setLinkedBudgetId(UUID.randomUUID());
        assertThatThrownBy(() -> service.patchPurchaseOrder(po.getId(), patch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Budget not found");
    }

    @Test
    @DisplayName("an approval replayed after the order moved on does not commit twice")
    void approve_isIdempotent() {
        approve();
        service.approvePurchaseOrder(po.getId());
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("400");
        assertThat(ledger.entries).hasSize(1);
        assertThat(ledger.entries.get(0).getIdempotencyKey())
                .isEqualTo(BudgetPosting.keyFor(BudgetLedgerKind.PO_COMMIT, po.getId()));
    }

    private void approve() {
        submitAs(maker);
        authenticate(checker);
        service.approvePurchaseOrder(po.getId());
    }

    private void submitAs(User user) {
        authenticate(user);
        service.submitPurchaseOrder(po.getId());
    }

    private User user(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setOrganisation(org);
        when(userRepository.findByEmailAndOrganisationId(email, org.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), "n/a", List.of()));
    }

    @Nested
    @DisplayName("maker-checker")
    class MakerChecker {
        @Test
        @DisplayName("the submitter cannot approve their own order")
        void submitterCannotApprove() {
            submitAs(maker);
            assertThatThrownBy(() -> service.approvePurchaseOrder(po.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot approve");
            assertThat(ledger.entries).isEmpty();
        }
    }
}
