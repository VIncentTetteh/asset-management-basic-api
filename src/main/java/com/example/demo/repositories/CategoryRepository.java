package com.example.demo.repositories;

import com.example.demo.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findByNameAndOrganisationId(String name, UUID organisationId);
    Set<Category> findByOrganisationId(UUID organisationId);
    Set<Category> findByParentCategoryId(UUID parentCategoryId);
}

