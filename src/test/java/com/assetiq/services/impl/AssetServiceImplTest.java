package com.assetiq.services.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.assetiq.dto.AssetDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.enums.NotificationType;
import com.assetiq.exceptions.ResourceNotFoundException;
import com.assetiq.models.Asset;
import com.assetiq.models.Category;
import com.assetiq.models.Department;
import com.assetiq.models.Location;
import com.assetiq.models.Organisation;
import com.assetiq.models.PurchaseOrder;
import com.assetiq.models.Supplier;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.EmailService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.UsageLimitService;
import com.assetiq.services.money.MoneyTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssetServiceImpl")
class AssetServiceImplTest {

    @Mock AssetRepository assetRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock LocationRepository locationRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock UserRepository userRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock EntityManager entityManager;
    @Mock UsageLimitService usageLimitService;
    @Mock AuditEventRepository auditEventRepository;
    @Mock AssetTransferRepository assetTransferRepository;
    @Mock MaintenanceRecordRepository maintenanceRecordRepository;
    @Mock DisposalRecordRepository disposalRecordRepository;
    @Mock NotificationService notificationService;
    @Mock EmailService emailService;
    @Mock CurrencyResolver currencyResolver;
    @Mock com.assetiq.repositories.CheckoutRecordRepository checkoutRecordRepository;

    private AssetServiceImpl service;
    private Organisation org;
    private Asset asset;
    private PurchaseOrder purchaseOrder;

    @BeforeEach
    void setUp() {
        service = new AssetServiceImpl(assetRepository, departmentRepository, organisationRepository,
                categoryRepository, locationRepository, supplierRepository, userRepository,
                purchaseOrderRepository, entityManager, usageLimitService, auditEventRepository,
                assetTransferRepository, maintenanceRecordRepository, disposalRecordRepository,
                notificationService, emailService, currencyResolver,
                MoneyTestSupport.aggregatorWithRates(Map.of("USD", "15")), checkoutRecordRepository);

        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));

        purchaseOrder = new PurchaseOrder();
        purchaseOrder.setId(UUID.randomUUID());
        asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setName("Laptop");
        asset.setOrganisation(org);
        asset.setStatus(AssetStatus.IN_USE);
        asset.setDepartment(entity(new Department()));
        asset.setLocation(entity(new Location()));
        asset.setSupplier(entity(new Supplier()));
        asset.setAssignedUser(entity(new User()));
        asset.setPurchaseOrder(purchaseOrder);

        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(asset.getId(), org))
                .thenReturn(Optional.of(asset));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currencyResolver.resolveOrDefault(any())).thenReturn("USD");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Nested
    @DisplayName("authorisation matches the controller")
    class Authorisation {

        @Test
        void orgAdminCanEdit() {
            authenticate("ROLE_ORG_ADMIN");
            assertThat(service.update(asset.getId(), rename("Edited")).getName()).isEqualTo("Edited");
        }

        @Test
        void customRoleWithEditAssetCanEdit() {
            authenticate("ROLE_FIELD_TECH", "EDIT_ASSET", "VIEW_ASSETS");
            assertThat(service.patch(asset.getId(), rename("Edited")).getName()).isEqualTo("Edited");
        }

        @Test
        void viewOnlyUserCannotEdit() {
            authenticate("ROLE_USER", "VIEW_ASSETS");
            assertThatThrownBy(() -> service.update(asset.getId(), rename("x")))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void deleteNeedsDeleteAsset() {
            authenticate("ROLE_FIELD_TECH", "EDIT_ASSET");
            assertThatThrownBy(() -> service.delete(asset.getId())).isInstanceOf(AccessDeniedException.class);

            authenticate("ROLE_FIELD_TECH", "DELETE_ASSET");
            service.delete(asset.getId());
            assertThat(asset.getDeletedAt()).isNotNull();
        }

        @Test
        void orgAdminCanDelete() {
            authenticate("ROLE_ORG_ADMIN");
            service.delete(asset.getId());
            assertThat(asset.getDeletedAt()).isNotNull();
        }

        @Test
        void unknownAssetIsNotFound() {
            authenticate("ROLE_ADMIN");
            assertThatThrownBy(() -> service.update(UUID.randomUUID(), rename("x")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("clearing optional relations")
    class Clearing {

        @BeforeEach
        void admin() {
            authenticate("ROLE_ADMIN");
        }

        @Test
        void untouchedRelationsArePreserved() {
            AssetDto result = service.update(asset.getId(), rename("Edited"));

            assertThat(asset.getPurchaseOrder()).isSameAs(purchaseOrder);
            assertThat(result.getPurchaseOrderId()).isEqualTo(purchaseOrder.getId());
            assertThat(asset.getDepartment()).isNotNull();
            assertThat(asset.getLocation()).isNotNull();
            assertThat(asset.getSupplier()).isNotNull();
            assertThat(asset.getAssignedUser()).isNotNull();
        }

        @Test
        void explicitClearsNullTheRelations() {
            AssetDto dto = rename("Edited");
            dto.setClearFields(List.of("departmentId", "locationId", "supplierId", "purchaseOrderId",
                    "assignedUserId"));

            AssetDto result = service.update(asset.getId(), dto);

            assertThat(asset.getDepartment()).isNull();
            assertThat(asset.getLocation()).isNull();
            assertThat(asset.getSupplier()).isNull();
            assertThat(asset.getPurchaseOrder()).isNull();
            assertThat(asset.getAssignedUser()).isNull();
            assertThat(result.getDepartmentId()).isNull();
            assertThat(result.getPurchaseOrderId()).isNull();
        }

        @Test
        void unknownClearFieldIsRejected() {
            AssetDto dto = rename("Edited");
            dto.setClearFields(List.of("name"));
            assertThatThrownBy(() -> service.update(asset.getId(), dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be cleared");
        }

        @Test
        void optionalScalarsAndCategoryCanBeCleared() {
            asset.setCategory(entity(new com.assetiq.models.Category()));
            asset.setAssetTag("LT-1");
            asset.setSerialNumber("SN");
            asset.setManufacturer("Dell");
            asset.setModel("XPS");
            asset.setDescription("desc");
            asset.setPurchaseDate(java.time.LocalDate.of(2024, 1, 1));
            asset.setWarrantyExpiryDate(java.time.LocalDate.of(2026, 1, 1));
            asset.setDepreciationMethod(com.assetiq.enums.DepreciationMethod.DECLINING_BALANCE);
            asset.setUsefulLifeMonths(24);
            asset.setResidualValue(java.math.BigDecimal.TEN);
            asset.setCostCenter("CC");
            AssetDto dto = rename("Edited");
            dto.setClearFields(List.of("categoryId", "assetTag", "serialNumber", "manufacturer", "model",
                    "description", "purchaseDate", "warrantyExpiryDate", "depreciationMethod",
                    "usefulLifeMonths", "residualValue", "costCenter"));

            service.update(asset.getId(), dto);

            assertThat(asset.getCategory()).isNull();
            assertThat(asset.getAssetTag()).isNull();
            assertThat(asset.getSerialNumber()).isNull();
            assertThat(asset.getManufacturer()).isNull();
            assertThat(asset.getModel()).isNull();
            assertThat(asset.getDescription()).isNull();
            assertThat(asset.getPurchaseDate()).isNull();
            assertThat(asset.getWarrantyExpiryDate()).isNull();
            assertThat(asset.getDepreciationMethod()).isNull();
            assertThat(asset.getUsefulLifeMonths()).isNull();
            assertThat(asset.getResidualValue()).isNull();
            assertThat(asset.getCostCenter()).isNull();
        }

        @Test
        void unassignReturnsAnInUseAssetToStock() {
            when(checkoutRecordRepository.findByAssetAndStatusAndDeletedAtIsNull(any(), any()))
                    .thenReturn(Optional.empty());
            service.unassignUser(asset.getId());
            assertThat(asset.getAssignedUser()).isNull();
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.IN_STOCK);
        }

        @Test
        void unassignKeepsACheckedOutAssetInUse() {
            when(checkoutRecordRepository.findByAssetAndStatusAndDeletedAtIsNull(any(), any()))
                    .thenReturn(Optional.of(new com.assetiq.models.CheckoutRecord()));
            service.unassignUser(asset.getId());
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.IN_USE);
        }

        @Test
        void setAndClearTogetherIsRejected() {
            AssetDto dto = rename("Edited");
            dto.setLocationId(UUID.randomUUID());
            dto.setClearFields(List.of("locationId"));
            assertThatThrownBy(() -> service.update(asset.getId(), dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("both set and cleared");
        }
    }

    @Nested
    @DisplayName("book value")
    class BookValue {

        @Test
        void updatePersistsBookValueFromTheEngine() {
            authenticate("ROLE_ADMIN");
            asset.setPurchaseCost(new BigDecimal("1200"));
            asset.setPurchaseDate(LocalDate.now().minusMonths(3));
            AssetDto dto = rename("Edited");
            dto.setUsefulLifeMonths(12);
            dto.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);

            AssetDto result = service.update(asset.getId(), dto);

            assertThat(asset.getCurrentBookValue()).isEqualByComparingTo("900.00");
            assertThat(result.getCurrentBookValue()).isEqualByComparingTo("900.00");
            assertThat(result.getAccumulatedDepreciation()).isEqualByComparingTo("300.00");
            assertThat(result.getMonthlyDepreciation()).isEqualByComparingTo("100.00");
            assertThat(result.getDepreciationConfigured()).isTrue();
        }

        @Test
        void createPersistsBookValueAndFlagsMissingSetup() {
            authenticate("ROLE_ADMIN");
            when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> {
                Asset a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });
            AssetDto dto = new AssetDto();
            dto.setName("Monitor");
            dto.setPurchaseCost(new BigDecimal("300"));
            dto.setPurchaseDate(LocalDate.now().minusMonths(5));

            AssetDto result = service.create(dto);

            assertThat(result.getCurrentBookValue()).isEqualByComparingTo("300.00");
            assertThat(result.getDepreciationConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("notification text")
    class NotificationText {

        @Test
        void createdAssetWithoutTagOmitsTagClause() {
            authenticate("ROLE_ADMIN");
            when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> {
                Asset a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });
            AssetDto dto = new AssetDto();
            dto.setName("Monitor");

            service.create(dto);

            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
            verify(notificationService).notifyOrgAdmins(eq(org), eq(NotificationType.SYSTEM), anyString(),
                    body.capture(), any(), anyString());
            assertThat(body.getValue()).isEqualTo("Asset 'Monitor' has been added to the inventory.")
                    .doesNotContain("null");
        }

        @Test
        void createdAssetWithTagKeepsTagClause() {
            authenticate("ROLE_ADMIN");
            when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> {
                Asset a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });
            AssetDto dto = new AssetDto();
            dto.setName("Monitor");
            dto.setAssetTag("IT-042");

            service.create(dto);

            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
            verify(notificationService).notifyOrgAdmins(eq(org), eq(NotificationType.SYSTEM), anyString(),
                    body.capture(), any(), anyString());
            assertThat(body.getValue()).isEqualTo("Asset 'Monitor' (tag: IT-042) has been added to the inventory.");
        }
    }

    @Test
    void historyCarriesTheInstantTheEventHappened() {
        java.time.Instant at = java.time.Instant.parse("2026-03-01T23:30:00Z");
        com.assetiq.models.MaintenanceRecord m = new com.assetiq.models.MaintenanceRecord();
        m.setId(UUID.randomUUID());
        m.setCreatedAt(at);
        when(maintenanceRecordRepository.findByAssetIdAndDeletedAtIsNull(asset.getId())).thenReturn(java.util.Set.of(m));

        var history = service.getHistory(asset.getId());

        assertThat(history).singleElement().extracting(e -> e.getOccurredAt()).isEqualTo(at);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSortsServerSideByTheRequestedColumn() {
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        when(assetRepository.findAll(any(Specification.class), pageable.capture()))
                .thenReturn(Page.empty());

        service.listPaged(new com.assetiq.dto.AssetFilterRequest(null, null, null, null, null, null, null, null,
                null, null, null, null, 0, 20, "currentBookValue,desc"));
        assertThat(pageable.getValue().getSort().getOrderFor("currentBookValue").isDescending()).isTrue();

        service.listPaged(new com.assetiq.dto.AssetFilterRequest(null, null, null, null, null, null, null, null,
                null, null, null, null, 0, 20, "organisation,asc"));
        assertThat(pageable.getValue().getSort().getOrderFor("name")).isNotNull();
    }

    @Test
    void statsCountPendingProcurementAndUnderRepair() {
        org.setBillingCurrency("GHS");
        when(assetRepository.countGroupedByStatus(org)).thenReturn(List.of(
                new Object[]{AssetStatus.PENDING_PROCUREMENT, 3L},
                new Object[]{AssetStatus.UNDER_REPAIR, 2L}));
        when(assetRepository.sumOnBookPurchaseCostByCurrency(org)).thenReturn(List.of());

        var stats = service.getStats();

        assertThat(stats.getPendingProcurement()).isEqualTo(3);
        assertThat(stats.getUnderRepair()).isEqualTo(2);
    }

    @Test
    @DisplayName("stats carry the register-wide value converted to the base currency")
    void statsCarryRegisterValue() {
        org.setBillingCurrency("GHS");
        when(assetRepository.countGroupedByStatus(org)).thenReturn(List.of());
        when(assetRepository.sumOnBookPurchaseCostByCurrency(org)).thenReturn(List.of(
                new Object[]{"GHS", new BigDecimal("1000")},
                new Object[]{"USD", new BigDecimal("100")},
                new Object[]{"JPY", new BigDecimal("5000")}));

        var stats = service.getStats();

        assertThat(stats.getTotalValue()).isEqualByComparingTo("2500.00");
        assertThat(stats.getCurrency()).isEqualTo("GHS");
        assertThat(stats.isComplete()).isFalse();
        assertThat(stats.getMissingRates()).containsExactly("JPY->GHS");
    }

    @Nested
    @DisplayName("TCO currency")
    class TcoCurrency {

        @Test
        @DisplayName("maintenance and disposal amounts are converted into the asset's currency")
        void convertsRecordCurrencies() {
            asset.setCurrency("GHS");
            asset.setPurchaseCost(new BigDecimal("1000"));
            com.assetiq.models.MaintenanceRecord usd = new com.assetiq.models.MaintenanceRecord();
            usd.setAsset(asset);
            usd.setCost(new BigDecimal("10"));
            usd.setCurrency("USD");
            com.assetiq.models.MaintenanceRecord legacy = new com.assetiq.models.MaintenanceRecord();
            legacy.setAsset(asset);
            legacy.setCost(new BigDecimal("50")); // no currency: the asset's
            com.assetiq.models.DisposalRecord sale = new com.assetiq.models.DisposalRecord();
            sale.setAsset(asset);
            sale.setSaleValue(new BigDecimal("2"));
            sale.setCurrency("USD");
            sale.setStatus(com.assetiq.enums.DisposalStatus.APPROVED);
            when(maintenanceRecordRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()))
                    .thenReturn(java.util.Set.of(usd, legacy));
            when(disposalRecordRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()))
                    .thenReturn(java.util.Set.of(sale));

            var tco = service.getTco(asset.getId());

            assertThat(tco.getCurrency()).isEqualTo("GHS");
            assertThat(tco.getTotalMaintenanceCost()).isEqualByComparingTo("200.00"); // 10 USD x 15 + 50
            assertThat(tco.getDisposalRecovery()).isEqualByComparingTo("30.00");
            assertThat(tco.getNetTco()).isEqualByComparingTo("1170.00");
            assertThat(tco.getComplete()).isTrue();
        }

        @Test
        @DisplayName("an amount without a rate is excluded and reported")
        void flagsMissingRate() {
            asset.setCurrency("GHS");
            com.assetiq.models.MaintenanceRecord eur = new com.assetiq.models.MaintenanceRecord();
            eur.setAsset(asset);
            eur.setCost(new BigDecimal("10"));
            eur.setCurrency("EUR");
            when(maintenanceRecordRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()))
                    .thenReturn(java.util.Set.of(eur));

            var tco = service.getTco(asset.getId());

            assertThat(tco.getTotalMaintenanceCost()).isEqualByComparingTo("0");
            assertThat(tco.getComplete()).isFalse();
            assertThat(tco.getMissingRates()).containsExactly("EUR->GHS");
        }
    }

    @Nested
    @DisplayName("create applies the category's prefix and default warranty")
    class CategoryDefaultsOnCreate {

        private Category laptops;

        @BeforeEach
        void category() {
            laptops = entity(new Category());
            laptops.setAssetPrefixCode("LAP");
            laptops.setDefaultWarrantyPeriodMonths(36);
            when(categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(laptops.getId(), org))
                    .thenReturn(Optional.of(laptops));
            when(assetRepository.findAssetTagsStartingWith(org, "LAP-")).thenReturn(List.of("LAP-0004"));
        }

        private AssetDto newAsset() {
            AssetDto dto = new AssetDto();
            dto.setName("MacBook");
            dto.setCategoryId(laptops.getId());
            dto.setPurchaseDate(LocalDate.of(2026, 3, 1));
            return dto;
        }

        @Test
        void missingTagAndWarrantyComeFromTheCategory() {
            AssetDto created = service.create(newAsset());

            assertThat(created.getAssetTag()).isEqualTo("LAP-0005");
            assertThat(created.getWarrantyExpiryDate()).isEqualTo(LocalDate.of(2029, 3, 1));
        }

        @Test
        void suppliedValuesWin() {
            AssetDto dto = newAsset();
            dto.setAssetTag("CUSTOM-1");
            dto.setWarrantyExpiryDate(LocalDate.of(2027, 1, 1));

            AssetDto created = service.create(dto);

            assertThat(created.getAssetTag()).isEqualTo("CUSTOM-1");
            assertThat(created.getWarrantyExpiryDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        }

        @Test
        void noPurchaseDateMeansNoWarrantyDefault() {
            AssetDto dto = newAsset();
            dto.setPurchaseDate(null);

            assertThat(service.create(dto).getWarrantyExpiryDate()).isNull();
        }
    }

    @Nested
    @DisplayName("status: DISPOSED only through the disposal workflow")
    class StatusRules {

        @BeforeEach
        void admin() {
            authenticate("ROLE_ORG_ADMIN");
        }

        private AssetDto status(AssetStatus s) {
            AssetDto dto = new AssetDto();
            dto.setStatus(s);
            return dto;
        }

        @Test
        void editCannotDispose() {
            assertThatThrownBy(() -> service.patch(asset.getId(), status(AssetStatus.DISPOSED)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("disposal request");
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.IN_USE);
        }

        @Test
        void createCannotDispose() {
            AssetDto dto = status(AssetStatus.DISPOSED);
            dto.setName("Old printer");
            assertThatThrownBy(() -> service.create(dto)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void disposedAssetCannotBeRevived() {
            asset.setStatus(AssetStatus.DISPOSED);
            assertThatThrownBy(() -> service.patch(asset.getId(), status(AssetStatus.IN_USE)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void retiringStaysAvailableWithoutAWorkflow() {
            assertThat(service.patch(asset.getId(), status(AssetStatus.RETIRED)).getStatus()).isEqualTo(AssetStatus.RETIRED);
        }

        @Test
        void resendingTheCurrentStatusIsANoOp() {
            asset.setStatus(AssetStatus.DISPOSED);
            AssetDto dto = status(AssetStatus.DISPOSED);
            dto.setName("Renamed");
            assertThat(service.patch(asset.getId(), dto).getName()).isEqualTo("Renamed");
        }
    }

    @Nested
    @DisplayName("TCO inputs are part of the asset contract")
    class TcoInputs {

        @BeforeEach
        void admin() {
            authenticate("ROLE_ORG_ADMIN");
        }

        @Test
        void savedReturnedAndClearable() {
            AssetDto dto = new AssetDto();
            dto.setInsurancePremiumPerYear(new BigDecimal("1200.00"));
            dto.setDowntimeCostPerDay(new BigDecimal("300.00"));
            dto.setInsurancePolicyExpiry(LocalDate.of(2027, 6, 30));

            AssetDto saved = service.patch(asset.getId(), dto);

            assertThat(saved.getInsurancePremiumPerYear()).isEqualByComparingTo("1200.00");
            assertThat(saved.getDowntimeCostPerDay()).isEqualByComparingTo("300.00");
            assertThat(saved.getInsurancePolicyExpiry()).isEqualTo(LocalDate.of(2027, 6, 30));

            AssetDto clear = new AssetDto();
            clear.setClearFields(List.of("insurancePremiumPerYear", "downtimeCostPerDay", "insurancePolicyExpiry"));
            AssetDto cleared = service.patch(asset.getId(), clear);

            assertThat(cleared.getInsurancePremiumPerYear()).isNull();
            assertThat(cleared.getDowntimeCostPerDay()).isNull();
            assertThat(cleared.getInsurancePolicyExpiry()).isNull();
        }

        @Test
        void parentAssetIsLinkedAndCyclesRefused() {
            Asset server = new Asset();
            server.setId(UUID.randomUUID());
            server.setName("Server");
            server.setOrganisation(org);
            when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(server.getId(), org)).thenReturn(Optional.of(server));
            AssetDto dto = new AssetDto();
            dto.setParentAssetId(server.getId());

            assertThat(service.patch(asset.getId(), dto).getParentAssetId()).isEqualTo(server.getId());

            server.setParentAsset(asset);
            AssetDto cycle = new AssetDto();
            cycle.setParentAssetId(server.getId());
            asset.setParentAsset(null);
            assertThatThrownBy(() -> service.patch(asset.getId(), cycle)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static AssetDto rename(String name) {
        AssetDto dto = new AssetDto();
        dto.setName(name);
        return dto;
    }

    private static <T extends com.assetiq.models.BaseEntity> T entity(T e) {
        e.setId(UUID.randomUUID());
        return e;
    }

    private static void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user@example.com", "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
