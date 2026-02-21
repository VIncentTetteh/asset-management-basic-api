package com.example.demo.controllers.v1;

import com.example.demo.dto.CategoryDto;
import com.example.demo.services.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryDto categoryDto,
                                                     @RequestParam UUID organisationId) {
        CategoryDto createdCategory = categoryService.createCategory(categoryDto, organisationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCategory);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> getCategoryById(@PathVariable UUID id) {
        CategoryDto category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(category);
    }

    @GetMapping
    public ResponseEntity<Set<CategoryDto>> getCategoriesByOrganisation(@RequestParam UUID organisationId) {
        Set<CategoryDto> categories = categoryService.getCategoriesByOrganisation(organisationId);
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{parentId}/sub-categories")
    public ResponseEntity<Set<CategoryDto>> getSubCategories(@PathVariable UUID parentId) {
        Set<CategoryDto> subCategories = categoryService.getSubCategories(parentId);
        return ResponseEntity.ok(subCategories);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto> updateCategory(@PathVariable UUID id,
                                                     @Valid @RequestBody CategoryDto categoryDto) {
        CategoryDto updatedCategory = categoryService.updateCategory(id, categoryDto);
        return ResponseEntity.ok(updatedCategory);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}

