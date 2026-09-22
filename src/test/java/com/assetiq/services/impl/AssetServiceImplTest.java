package com.assetiq.services.impl;

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
                MoneyTestSupport.aggregatorWithRates(Map.of("USD", "15")));

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
