package com.example.demo.services.impl;

import com.example.demo.dto.CategoryDto;
import com.example.demo.models.Category;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.CategoryRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.CategoryService;
import com.example.demo.services.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CategoryServiceImpl extends TenantAwareService implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository,
            OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.categoryRepository = categoryRepository;
    }

    @Override
    public CategoryDto createCategory(CategoryDto categoryDto, UUID organisationId) {
        // organisationId param is ignored — always use tenant context
        Organisation org = requireTenantOrg();

        Category category = new Category();
        category.setName(categoryDto.getName());
        category.setAssetPrefixCode(categoryDto.getAssetPrefixCode());
        category.setDefaultWarrantyPeriodMonths(categoryDto.getDefaultWarrantyPeriodMonths());
        category.setOrganisation(org);

        if (categoryDto.getParentCategoryId() != null) {
            Category parentCategory = categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    categoryDto.getParentCategoryId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found in your organisation"));
            category.setParentCategory(parentCategory);
        }

        return mapToDto(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDto getCategoryById(UUID id) {
        Organisation org = requireTenantOrg();
        Category category = categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        return mapToDto(category);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CategoryDto> getCategoriesByOrganisation(UUID organisationId) {
        // Always scope to tenant context, ignore param
        Organisation org = requireTenantOrg();
        return categoryRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CategoryDto> getSubCategories(UUID parentCategoryId) {
        Organisation org = requireTenantOrg();
        categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(parentCategoryId, org)
                .orElseThrow(() -> new IllegalArgumentException("Parent category not found in your organisation"));
        return categoryRepository.findByParentCategoryIdAndDeletedAtIsNull(parentCategoryId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public CategoryDto updateCategory(UUID id, CategoryDto categoryDto) {
        Organisation org = requireTenantOrg();
        Category category = categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        category.setName(categoryDto.getName());
        category.setAssetPrefixCode(categoryDto.getAssetPrefixCode());
        category.setDefaultWarrantyPeriodMonths(categoryDto.getDefaultWarrantyPeriodMonths());

        return mapToDto(categoryRepository.save(category));
    }

    @Override
    public CategoryDto patchCategory(UUID id, CategoryDto categoryDto) {
        Organisation org = requireTenantOrg();
        Category category = categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        if (categoryDto.getName() != null) {
            category.setName(categoryDto.getName());
        }
        if (categoryDto.getAssetPrefixCode() != null) {
            category.setAssetPrefixCode(categoryDto.getAssetPrefixCode());
        }
        if (categoryDto.getDefaultWarrantyPeriodMonths() != null) {
            category.setDefaultWarrantyPeriodMonths(categoryDto.getDefaultWarrantyPeriodMonths());
        }
        if (categoryDto.getParentCategoryId() != null) {
            Category parentCategory = categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    categoryDto.getParentCategoryId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found in your organisation"));
            category.setParentCategory(parentCategory);
        }

        return mapToDto(categoryRepository.save(category));
    }

    @Override
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
