package com.assetiq.services.impl;

import com.assetiq.dto.CategoryDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.models.Asset;
import com.assetiq.models.Category;
import com.assetiq.models.DepreciationPolicy;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.CategoryRepository;
import com.assetiq.repositories.DepreciationPolicyRepository;
import com.assetiq.repositories.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CategoryServiceImpl depreciation policy")
class CategoryServiceImplTest {

    @Mock CategoryRepository categoryRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock DepreciationPolicyRepository depreciationPolicyRepository;
    @Mock AssetRepository assetRepository;

    private CategoryServiceImpl service;
    private Organisation org;
    private Category category;
    private DepreciationPolicy policy;
    private Asset asset;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(categoryRepository, organisationRepository,
                depreciationPolicyRepository, assetRepository);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));

        category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Laptops");
        category.setOrganisation(org);
        when(categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(category.getId(), org))
                .thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        policy = new DepreciationPolicy();
        policy.setId(UUID.randomUUID());
        policy.setMethod(DepreciationMethod.STRAIGHT_LINE);
        policy.setUsefulLifeMonths(12);
        when(depreciationPolicyRepository.findByIdAndOrganisationAndDeletedAtIsNull(policy.getId(), org))
                .thenReturn(Optional.of(policy));

        asset = new Asset();
        asset.setStatus(AssetStatus.IN_USE);
        asset.setCategory(category);
        asset.setPurchaseCost(new BigDecimal("1200"));
        asset.setPurchaseDate(LocalDate.now().minusMonths(3));
        asset.setDepreciationMethod(null);
        asset.setCurrentBookValue(new BigDecimal("1200"));
        when(assetRepository.findByOrganisationAndCategoryIdAndDeletedAtIsNull(org, category.getId()))
                .thenReturn(Set.of(asset));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void patchAssignsPolicyAndRevaluesAssets() {
        CategoryDto dto = new CategoryDto();
        dto.setDepreciationPolicyId(policy.getId());

        CategoryDto result = service.patchCategory(category.getId(), dto);

        assertThat(result.getDepreciationPolicyId()).isEqualTo(policy.getId());
        assertThat(asset.getCurrentBookValue()).isEqualByComparingTo("900.00");
    }

    @Test
    void untouchedPolicyIsKept() {
        category.setDepreciationPolicy(policy);
        CategoryDto dto = new CategoryDto();
        dto.setName("Notebooks");

        assertThat(service.patchCategory(category.getId(), dto).getDepreciationPolicyId()).isEqualTo(policy.getId());
    }

    @Test
    void explicitClearRemovesPolicy() {
        category.setDepreciationPolicy(policy);
        CategoryDto dto = new CategoryDto();
        dto.setClearFields(List.of("depreciationPolicyId"));

        assertThat(service.patchCategory(category.getId(), dto).getDepreciationPolicyId()).isNull();
        assertThat(asset.getCurrentBookValue()).isEqualByComparingTo("1200.00");
    }

    @Test
    void descriptionPrefixAndWarrantyCanBeCleared() {
        category.setDescription("Portable computers");
        category.setAssetPrefixCode("LT");
        category.setDefaultWarrantyPeriodMonths(24);
        CategoryDto dto = new CategoryDto();
        dto.setClearFields(List.of("description", "assetPrefixCode", "defaultWarrantyPeriodMonths"));

        CategoryDto result = service.patchCategory(category.getId(), dto);

        assertThat(result.getDescription()).isNull();
        assertThat(result.getAssetPrefixCode()).isNull();
        assertThat(result.getDefaultWarrantyPeriodMonths()).isNull();
    }

    @Test
    void clearingAndSettingTheSameFieldIsRejected() {
        CategoryDto dto = new CategoryDto();
        dto.setAssetPrefixCode("LT");
        dto.setClearFields(List.of("assetPrefixCode"));

        assertThatThrownBy(() -> service.patchCategory(category.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both set and cleared");
    }

    @Test
    void foreignPolicyIsRejected() {
        CategoryDto dto = new CategoryDto();
        dto.setDepreciationPolicyId(UUID.randomUUID());
        assertThatThrownBy(() -> service.patchCategory(category.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Category child(Category parent, String name) {
        Category c = new Category();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setOrganisation(org);
        c.setParentCategory(parent);
        when(categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(c.getId(), org)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void parentCannotBeADescendant() {
        Category sub = child(category, "Ultrabooks");
        Category subSub = child(sub, "13-inch");
        CategoryDto dto = new CategoryDto();
        dto.setParentCategoryId(subSub.getId());

        assertThatThrownBy(() -> service.patchCategory(category.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own sub-categories");
        assertThatThrownBy(() -> service.updateCategory(category.getId(), fullReplace(sub.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(category.getParentCategory()).isNull();
    }

    @Test
    void parentCannotBeItself() {
        CategoryDto dto = new CategoryDto();
        dto.setParentCategoryId(category.getId());

        assertThatThrownBy(() -> service.patchCategory(category.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("its own parent");
    }

    @Test
    void unrelatedParentIsAccepted() {
        Category hardware = child(null, "Hardware");
        CategoryDto dto = new CategoryDto();
        dto.setParentCategoryId(hardware.getId());

        assertThat(service.patchCategory(category.getId(), dto).getParentCategoryId()).isEqualTo(hardware.getId());
    }

    @Test
    void fullReplaceSetsAndClearsTheParent() {
        Category hardware = child(null, "Hardware");

        service.updateCategory(category.getId(), fullReplace(hardware.getId()));
        assertThat(category.getParentCategory()).isEqualTo(hardware);

        service.updateCategory(category.getId(), fullReplace(null));
        assertThat(category.getParentCategory()).isNull();
    }

    private static CategoryDto fullReplace(UUID parentId) {
        CategoryDto dto = new CategoryDto();
        dto.setName("Laptops");
        dto.setParentCategoryId(parentId);
        return dto;
    }
}
