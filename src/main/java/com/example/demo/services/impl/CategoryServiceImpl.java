package com.example.demo.services.impl;

import com.example.demo.dto.CategoryDto;
import com.example.demo.models.Category;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.CategoryRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final OrganisationRepository organisationRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository, OrganisationRepository organisationRepository) {
        this.categoryRepository = categoryRepository;
        this.organisationRepository = organisationRepository;
    }

    @Override
    public CategoryDto createCategory(CategoryDto categoryDto, UUID organisationId) {
        Organisation organisation = organisationRepository.findById(organisationId)
            .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));

        Category category = new Category();
        category.setName(categoryDto.getName());
        category.setAssetPrefixCode(categoryDto.getAssetPrefixCode());
        category.setDefaultWarrantyPeriodMonths(categoryDto.getDefaultWarrantyPeriodMonths());
        category.setOrganisation(organisation);

        if (categoryDto.getParentCategoryId() != null) {
            Category parentCategory = categoryRepository.findById(categoryDto.getParentCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            category.setParentCategory(parentCategory);
        }

        Category savedCategory = categoryRepository.save(category);
        return mapToDto(savedCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDto getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        return mapToDto(category);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CategoryDto> getCategoriesByOrganisation(UUID organisationId) {
        return categoryRepository.findByOrganisationId(organisationId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CategoryDto> getSubCategories(UUID parentCategoryId) {
        return categoryRepository.findByParentCategoryId(parentCategoryId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public CategoryDto updateCategory(UUID id, CategoryDto categoryDto) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        category.setName(categoryDto.getName());
        category.setAssetPrefixCode(categoryDto.getAssetPrefixCode());
        category.setDefaultWarrantyPeriodMonths(categoryDto.getDefaultWarrantyPeriodMonths());

        Category updatedCategory = categoryRepository.save(category);
        return mapToDto(updatedCategory);
    }

    @Override
    public void deleteCategory(UUID id) {
        categoryRepository.deleteById(id);
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
        return dto;
    }
}

