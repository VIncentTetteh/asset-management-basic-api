package com.example.demo.services;

import com.example.demo.dto.CategoryDto;
import java.util.Set;
import java.util.UUID;

public interface CategoryService {
    CategoryDto createCategory(CategoryDto categoryDto, UUID organisationId);
    CategoryDto getCategoryById(UUID id);
    Set<CategoryDto> getCategoriesByOrganisation(UUID organisationId);
    Set<CategoryDto> getSubCategories(UUID parentCategoryId);
    CategoryDto updateCategory(UUID id, CategoryDto categoryDto);
    void deleteCategory(UUID id);
}

