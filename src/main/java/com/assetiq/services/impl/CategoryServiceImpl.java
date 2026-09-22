package com.assetiq.services.impl;

import com.assetiq.dto.CategoryDto;
import com.assetiq.config.CachingConfig;
import com.assetiq.enums.AssetStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.Category;
import com.assetiq.models.DepreciationPolicy;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.CategoryRepository;
import com.assetiq.repositories.DepreciationPolicyRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.finance.DepreciationCalculator;
import com.assetiq.services.CategoryService;
import com.assetiq.services.HierarchyGuard;
import com.assetiq.services.TenantAwareService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CategoryServiceImpl extends TenantAwareService implements CategoryService {

    /** Relations an update may clear through {@link CategoryDto#getClearFields()}. */
    static final Set<String> CLEARABLE_FIELDS = Set.of("depreciationPolicyId", "parentCategoryId");

    private final CategoryRepository categoryRepository;
    private final DepreciationPolicyRepository depreciationPolicyRepository;
    private final AssetRepository assetRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository,
            OrganisationRepository organisationRepository,
            DepreciationPolicyRepository depreciationPolicyRepository,
            AssetRepository assetRepository) {
        super(organisationRepository);
        this.categoryRepository = categoryRepository;
        this.depreciationPolicyRepository = depreciationPolicyRepository;
        this.assetRepository = assetRepository;
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.CATEGORIES, allEntries = true)
    public CategoryDto createCategory(CategoryDto categoryDto, UUID organisationId) {
        // organisationId param is ignored — always use tenant context
        Organisation org = requireTenantOrg();

        Category category = new Category();
        category.setName(categoryDto.getName());
        category.setDescription(categoryDto.getDescription());
        category.setAssetPrefixCode(categoryDto.getAssetPrefixCode());
        category.setDefaultWarrantyPeriodMonths(categoryDto.getDefaultWarrantyPeriodMonths());
        category.setOrganisation(org);

        if (categoryDto.getParentCategoryId() != null) {
            category.setParentCategory(requireParent(categoryDto.getParentCategoryId(), null, org));
        }
        if (categoryDto.getDepreciationPolicyId() != null) {
            category.setDepreciationPolicy(requirePolicy(categoryDto.getDepreciationPolicyId(), org));
        }

        return mapToDto(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.CATEGORIES, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':one:' + #id.toString()")
    public CategoryDto getCategoryById(UUID id) {
        Organisation org = requireTenantOrg();
        Category category = categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        return mapToDto(category);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.CATEGORIES, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':list'")
    public Set<CategoryDto> getCategoriesByOrganisation(UUID organisationId) {
        // Always scope to tenant context, ignore param
        Organisation org = requireTenantOrg();
        return categoryRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.CATEGORIES, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':children:' + #parentCategoryId.toString()")
    public Set<CategoryDto> getSubCategories(UUID parentCategoryId) {
        Organisation org = requireTenantOrg();
        categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(parentCategoryId, org)
                .orElseThrow(() -> new IllegalArgumentException("Parent category not found in your organisation"));
        return categoryRepository.findByParentCategoryIdAndDeletedAtIsNull(parentCategoryId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.CATEGORIES, allEntries = true)
    public CategoryDto updateCategory(UUID id, CategoryDto categoryDto) {
        Organisation org = requireTenantOrg();
        Category category = categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        category.setName(categoryDto.getName());
        category.setDescription(categoryDto.getDescription());
        category.setAssetPrefixCode(categoryDto.getAssetPrefixCode());
        category.setDefaultWarrantyPeriodMonths(categoryDto.getDefaultWarrantyPeriodMonths());
        // Full replace: the parent is set from the body, and a missing one moves the
        // category to the top level (PUT used to ignore the parent entirely).
        category.setParentCategory(categoryDto.getParentCategoryId() != null
                ? requireParent(categoryDto.getParentCategoryId(), id, org)
                : null);
        applyDepreciationPolicyAndClears(category, categoryDto, org);

        return mapToDto(categoryRepository.save(category));
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.CATEGORIES, allEntries = true)
    public CategoryDto patchCategory(UUID id, CategoryDto categoryDto) {
        Organisation org = requireTenantOrg();
        Category category = categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        if (categoryDto.getName() != null) {
            category.setName(categoryDto.getName());
        }
        if (categoryDto.getDescription() != null) {
            category.setDescription(categoryDto.getDescription());
        }
        if (categoryDto.getAssetPrefixCode() != null) {
            category.setAssetPrefixCode(categoryDto.getAssetPrefixCode());
        }
        if (categoryDto.getDefaultWarrantyPeriodMonths() != null) {
            category.setDefaultWarrantyPeriodMonths(categoryDto.getDefaultWarrantyPeriodMonths());
        }
        if (categoryDto.getParentCategoryId() != null) {
            category.setParentCategory(requireParent(categoryDto.getParentCategoryId(), id, org));
        }
        applyDepreciationPolicyAndClears(category, categoryDto, org);

        return mapToDto(categoryRepository.save(category));
    }

    /**
     * Sets or clears the depreciation policy (and clears the parent when asked).
     * A policy change revalues the category's assets immediately, so the stored
     * book value does not wait for the monthly job.
     */
    private void applyDepreciationPolicyAndClears(Category category, CategoryDto dto, Organisation org) {
        Set<String> clears = dto.getClearFields() == null ? Set.of() : Set.copyOf(dto.getClearFields());
        for (String field : clears) {
            if (!CLEARABLE_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Field cannot be cleared: " + field);
            }
        }
        if (clears.contains("depreciationPolicyId") && dto.getDepreciationPolicyId() != null) {
            throw new IllegalArgumentException("Field is both set and cleared: depreciationPolicyId");
        }
        if (clears.contains("parentCategoryId")) {
            if (dto.getParentCategoryId() != null) {
                throw new IllegalArgumentException("Field is both set and cleared: parentCategoryId");
            }
            category.setParentCategory(null);
        }

        UUID before = category.getDepreciationPolicy() != null ? category.getDepreciationPolicy().getId() : null;
        if (dto.getDepreciationPolicyId() != null) {
            category.setDepreciationPolicy(requirePolicy(dto.getDepreciationPolicyId(), org));
        } else if (clears.contains("depreciationPolicyId")) {
            category.setDepreciationPolicy(null);
        }
        UUID after = category.getDepreciationPolicy() != null ? category.getDepreciationPolicy().getId() : null;
        if (!Objects.equals(before, after) && category.getId() != null) {
            LocalDate today = LocalDate.now();
            for (Asset asset : assetRepository.findByOrganisationAndCategoryIdAndDeletedAtIsNull(org, category.getId())) {
                if (asset.getStatus() != AssetStatus.DISPOSED) {
                    asset.setCurrentBookValue(DepreciationCalculator.forAsset(asset, today).netBookValue());
                }
            }
        }
    }

    /**
     * Loads a parent category and refuses one that is the category itself or one of
     * its sub-categories, which would make a cycle.
     */
    private Category requireParent(UUID parentId, UUID selfId, Organisation org) {
        Category parent = categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(parentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Parent category not found in your organisation"));
        if (parent.getId().equals(selfId)) {
            throw new IllegalArgumentException("A category cannot be its own parent");
        }
        HierarchyGuard.assertNotDescendant(parent, selfId, Category::getParentCategory, Category::getId,
                "A category cannot be placed under one of its own sub-categories");
        return parent;
    }

    private DepreciationPolicy requirePolicy(UUID policyId, Organisation org) {
        return depreciationPolicyRepository.findByIdAndOrganisationAndDeletedAtIsNull(policyId, org)
                .orElseThrow(() -> new IllegalArgumentException("Depreciation policy not found in your organisation"));
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.CATEGORIES, allEntries = true)
    public void deleteCategory(UUID id) {
        Organisation org = requireTenantOrg();
        Category category = categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        category.setDeletedAt(Instant.now());
        categoryRepository.save(category);
    }

    private CategoryDto mapToDto(Category category) {
        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setAssetPrefixCode(category.getAssetPrefixCode());
        dto.setDefaultWarrantyPeriodMonths(category.getDefaultWarrantyPeriodMonths());
        if (category.getParentCategory() != null) {
            dto.setParentCategoryId(category.getParentCategory().getId());
        }
        if (category.getDepreciationPolicy() != null) {
            dto.setDepreciationPolicyId(category.getDepreciationPolicy().getId());
        }
        dto.setOrganisationId(category.getOrganisation().getId());
        dto.setCreatedAt(category.getCreatedAt());
        dto.setUpdatedAt(category.getUpdatedAt());
        return dto;
    }
}
