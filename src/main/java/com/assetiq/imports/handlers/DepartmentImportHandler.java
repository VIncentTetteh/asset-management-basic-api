package com.assetiq.imports.handlers;

import com.assetiq.dto.DepartmentDto;
import com.assetiq.enums.DepartmentStatus;
import com.assetiq.imports.AbstractImportRunner;
import com.assetiq.imports.FieldValidationException;
import com.assetiq.imports.ImportBeanValidator;
import com.assetiq.imports.ImportDataType;
import com.assetiq.imports.ImportEntityHandler;
import com.assetiq.imports.ImportEntityType;
import com.assetiq.imports.ImportFieldDescriptor;
import com.assetiq.imports.ImportOptions;
import com.assetiq.imports.ImportReferenceResolver;
import com.assetiq.imports.ImportRow;
import com.assetiq.imports.ImportRunReport;
import com.assetiq.models.Department;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.services.DepartmentService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.assetiq.imports.ImportFieldDescriptor.enumField;
import static com.assetiq.imports.ImportFieldDescriptor.field;

/**
 * Departments. Duplicates are decided on department code where the row has one — a code
 * is the stable identifier in every HR export — and otherwise on name.
 */
@Component
public class DepartmentImportHandler implements ImportEntityHandler {

    private static final List<ImportFieldDescriptor> FIELDS = List.of(
            field("name", "Department name", ImportDataType.STRING).required()
                    .example("Finance")
                    .notes("Must be unique within your organisation.")
                    .aliases("department", "department title", "dept", "dept name", "name",
                            "org unit", "business unit", "team", "division",
                            "section").build(),
            field("departmentCode", "Department code", ImportDataType.STRING)
                    .example("FIN")
                    .notes("Must be unique within your organisation when given.")
                    .aliases("dept code", "dept id", "dept no", "code", "department id",
                            "unit code", "unit id", "org unit code").build(),
            field("description", "Description", ImportDataType.TEXT)
                    .example("Finance and treasury")
                    .aliases("notes", "details", "about", "comments").build(),
            field("costCenterCode", "Cost centre code", ImportDataType.STRING)
                    .example("CC-1001")
                    .notes("Must be unique within your organisation when given.")
                    .aliases("cost center", "cost centre", "cost center code", "cost centre code",
                            "cost center id", "cc code", "gl code", "gl account",
                            "costcentre").build(),
            field("budgetLimit", "Budget limit", ImportDataType.DECIMAL)
                    .example("250000.00")
                    .notes("A planning cap in your organisation's base currency. Leave blank for none.")
                    .aliases("budget", "budget amount", "annual budget", "allocated budget",
                            "budget cap", "spend limit").build(),
            enumField("status", "Status", DepartmentStatus.class)
                    .example("ACTIVE")
                    .notes("Defaults to ACTIVE when blank.")
                    .aliases("department status", "dept status", "state").build(),
            field("parentDepartment", "Parent department", ImportDataType.REFERENCE)
                    .example("")
                    .notes("Name or code of another department. It may appear in an earlier row of this file.")
                    .aliases("parent", "parent dept", "parent department name", "parent unit",
                            "reports to", "belongs to").build(),
            field("manager", "Manager", ImportDataType.REFERENCE)
                    .example("")
                    .notes("Email address or employee number of an existing AssetIQ user.")
                    .aliases("manager email", "head", "head email", "department head",
                            "owner", "owner email", "hod").build()
    );

    private final DepartmentService departmentService;
    private final DepartmentRepository departmentRepository;
    private final ImportReferenceResolver referenceResolver;
    private final ImportBeanValidator beanValidator;

    public DepartmentImportHandler(DepartmentService departmentService,
                                   DepartmentRepository departmentRepository,
                                   ImportReferenceResolver referenceResolver,
                                   ImportBeanValidator beanValidator) {
        this.departmentService = departmentService;
        this.departmentRepository = departmentRepository;
        this.referenceResolver = referenceResolver;
        this.beanValidator = beanValidator;
    }

    @Override
    public ImportEntityType entityType() {
        return ImportEntityType.DEPARTMENTS;
    }

    @Override
    public List<ImportFieldDescriptor> fields() {
        return FIELDS;
    }

    @Override
    public ImportRunner runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
        return new Runner(organisation, options, report);
    }

    private final class Runner extends AbstractImportRunner<DepartmentDto> {

        private final ImportReferenceResolver.Refs refs;
        private final Map<String, UUID> byName = new LinkedHashMap<>();
        private final Map<String, UUID> byCode = new LinkedHashMap<>();

        private Runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
            super(options);
            this.refs = referenceResolver.open(organisation, options, report);
            for (Department department : departmentRepository.findAllByOrganisationAndDeletedAtIsNull(organisation)) {
                if (department.getName() != null) {
                    byName.putIfAbsent(key(department.getName()), department.getId());
                }
                if (department.getDepartmentCode() != null && !department.getDepartmentCode().isBlank()) {
                    byCode.putIfAbsent(key(department.getDepartmentCode()), department.getId());
                }
            }
        }

        @Override
        protected DepartmentDto build(ImportRow row) {
            DepartmentDto dto = new DepartmentDto();
            dto.setName(row.requiredString("name"));
            dto.setDepartmentCode(row.string("departmentCode"));
            dto.setDescription(row.string("description"));
            dto.setCostCenterCode(row.string("costCenterCode"));
            dto.setBudgetLimit(row.decimal("budgetLimit"));
            dto.setStatus(row.enumValue(DepartmentStatus.class, "status"));
            dto.setManagerId(refs.user(row.string("manager"), "manager"));

            String parent = row.string("parentDepartment");
            if (parent != null) {
                UUID parentId = byCode.getOrDefault(key(parent), byName.get(key(parent)));
                if (parentId == null) {
                    throw new FieldValidationException("parentDepartment",
                            "names '" + parent + "', which is not a department in your organisation."
                                    + " Put the parent in an earlier row, or create it first.");
                }
                dto.setParentDepartmentId(parentId);
            }
            beanValidator.validateForCreate(dto);
            return dto;
        }

        @Override
        protected UUID findExisting(ImportRow row, DepartmentDto payload) {
            if (payload.getDepartmentCode() != null && !payload.getDepartmentCode().isBlank()) {
                UUID byCodeMatch = byCode.get(key(payload.getDepartmentCode()));
                if (byCodeMatch != null) return byCodeMatch;
            }
            return byName.get(key(payload.getName()));
        }

        @Override
        protected void create(DepartmentDto payload) {
            DepartmentDto created = departmentService.create(payload);
            byName.put(key(payload.getName()), created.getId());
            if (payload.getDepartmentCode() != null && !payload.getDepartmentCode().isBlank()) {
                byCode.put(key(payload.getDepartmentCode()), created.getId());
            }
        }

        @Override
        protected void update(UUID id, DepartmentDto payload) {
            departmentService.patch(id, payload);
        }

        @Override
        protected String describe() {
            return "department";
        }

        @Override
        protected String naturalKey(ImportRow row, DepartmentDto payload) {
            return payload.getDepartmentCode() != null && !payload.getDepartmentCode().isBlank()
                    ? "department code '" + payload.getDepartmentCode() + "'"
                    : "department name '" + payload.getName() + "'";
        }

        private String key(String value) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
