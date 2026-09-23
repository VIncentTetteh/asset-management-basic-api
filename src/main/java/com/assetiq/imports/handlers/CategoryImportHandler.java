package com.assetiq.imports.handlers;

import com.assetiq.dto.CategoryDto;
import com.assetiq.imports.AbstractImportRunner;
import com.assetiq.imports.FieldValidationException;
import com.assetiq.imports.ImportBeanValidator;
import com.assetiq.imports.ImportDataType;
import com.assetiq.imports.ImportEntityHandler;
import com.assetiq.imports.ImportEntityType;
import com.assetiq.imports.ImportFieldDescriptor;
import com.assetiq.imports.ImportOptions;
import com.assetiq.imports.ImportRow;
import com.assetiq.imports.ImportRunReport;
import com.assetiq.models.Category;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.CategoryRepository;
import com.assetiq.services.CategoryService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.assetiq.imports.ImportFieldDescriptor.field;

/** Asset categories. Duplicates are decided on category name, case-insensitively. */
@Component
public class CategoryImportHandler implements ImportEntityHandler {

    private static final List<ImportFieldDescriptor> FIELDS = List.of(
            field("name", "Category name", ImportDataType.STRING).required()
                    .example("Laptops")
                    .notes("Must be unique within your organisation.")
                    .aliases("category", "category title", "asset category", "name",
                            "class", "asset class", "type", "asset type",
                            "group", "asset group", "classification", "family").build(),
            field("description", "Description", ImportDataType.TEXT)
                    .example("Portable computers issued to staff")
                    .aliases("notes", "details", "about", "comments").build(),
            field("assetPrefixCode", "Asset tag prefix", ImportDataType.STRING)
                    .example("LT")
                    .notes("Used to generate asset tags such as LT-000123.")
                    .aliases("prefix", "prefix code", "tag prefix", "code", "category code",
                            "category prefix", "asset prefix").build(),
            field("defaultWarrantyPeriodMonths", "Default warranty (months)", ImportDataType.INTEGER)
                    .example("24")
                    .notes("Whole number of months. Applied to new assets in this category.")
                    .aliases("warranty months", "warranty (months)", "default warranty",
                            "default warranty months", "warranty period",
                            "warranty period months", "warranty duration months").build(),
            field("parentCategory", "Parent category", ImportDataType.REFERENCE)
                    .example("")
                    .notes("Name of another category. It may appear in an earlier row of this file.")
                    .aliases("parent", "parent class", "parent category name", "parent group",
                            "belongs to", "sub category of").build()
    );

    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;
    private final ImportBeanValidator beanValidator;

    public CategoryImportHandler(CategoryService categoryService,
                                 CategoryRepository categoryRepository,
                                 ImportBeanValidator beanValidator) {
        this.categoryService = categoryService;
        this.categoryRepository = categoryRepository;
        this.beanValidator = beanValidator;
    }

    @Override
    public ImportEntityType entityType() {
        return ImportEntityType.CATEGORIES;
    }

    @Override
    public List<ImportFieldDescriptor> fields() {
        return FIELDS;
    }

    @Override
    public ImportRunner runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
        return new Runner(organisation, options, report);
    }

    private final class Runner extends AbstractImportRunner<CategoryDto> {

        private final Organisation organisation;
        private final Map<String, UUID> byName = new LinkedHashMap<>();

        private Runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
            super(options);
            this.organisation = organisation;
            for (Category category : categoryRepository.findByOrganisationAndDeletedAtIsNull(organisation)) {
                if (category.getName() != null) {
                    byName.putIfAbsent(key(category.getName()), category.getId());
                }
            }
        }

        @Override
        protected CategoryDto build(ImportRow row) {
            CategoryDto dto = new CategoryDto();
            dto.setName(row.requiredString("name"));
            dto.setDescription(row.string("description"));
            dto.setAssetPrefixCode(row.string("assetPrefixCode"));
            dto.setDefaultWarrantyPeriodMonths(row.integer("defaultWarrantyPeriodMonths"));

            String parent = row.string("parentCategory");
            if (parent != null) {
                UUID parentId = byName.get(key(parent));
                if (parentId == null) {
                    throw new FieldValidationException("parentCategory",
                            "names '" + parent + "', which is not a category in your organisation."
                                    + " Put the parent in an earlier row, or create it first.");
                }
                dto.setParentCategoryId(parentId);
            }
            beanValidator.validateForCreate(dto);
            return dto;
        }

        @Override
        protected UUID findExisting(ImportRow row, CategoryDto payload) {
            return byName.get(key(payload.getName()));
        }

        @Override
        protected void create(CategoryDto payload) {
            CategoryDto created = categoryService.createCategory(payload, organisation.getId());
            byName.put(key(payload.getName()), created.getId());
        }

        @Override
        protected void update(UUID id, CategoryDto payload) {
            categoryService.patchCategory(id, payload);
        }

        @Override
        protected String describe() {
            return "category";
        }

        @Override
        protected String naturalKey(ImportRow row, CategoryDto payload) {
            return "category name '" + payload.getName() + "'";
        }

        private String key(String value) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
