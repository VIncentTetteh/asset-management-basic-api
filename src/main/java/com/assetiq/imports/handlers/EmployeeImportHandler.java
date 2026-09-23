package com.assetiq.imports.handlers;

import com.assetiq.dto.EmployeeDto;
import com.assetiq.enums.EmployeeStatus;
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
import com.assetiq.models.Employee;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.EmployeeRepository;
import com.assetiq.services.EmployeeService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.assetiq.imports.ImportFieldDescriptor.enumField;
import static com.assetiq.imports.ImportFieldDescriptor.field;

/**
 * Employees. Duplicates are decided on employee number where the row has one, and
 * otherwise on email — the two identifiers an HR export actually carries. Two people
 * can share a name, so a name is never treated as a duplicate signal.
 */
@Component
public class EmployeeImportHandler implements ImportEntityHandler {

    private static final List<ImportFieldDescriptor> FIELDS = List.of(
            field("employeeNumber", "Employee number", ImportDataType.STRING)
                    .example("EMP-00042")
                    .notes("Your HR system's identifier. Used to match rows to existing people.")
                    .aliases("employee id", "staff id", "staff number", "payroll number",
                            "emp no", "personnel number", "badge number").build(),
            field("firstName", "First name", ImportDataType.STRING).required()
                    .example("Ama")
                    .aliases("given name", "forename", "first").build(),
            field("lastName", "Last name", ImportDataType.STRING).required()
                    .example("Mensah")
                    .aliases("surname", "family name", "last", "lastname").build(),
            field("email", "Email", ImportDataType.EMAIL)
                    .example("ama.mensah@example.com")
                    .notes("Used to match rows to existing people when there is no employee number.")
                    .aliases("email address", "work email", "e-mail", "company email").build(),
            field("phone", "Phone", ImportDataType.STRING)
                    .example("+233201234567")
                    .aliases("telephone", "mobile", "phone number", "contact number", "cell").build(),
            field("jobTitle", "Job title", ImportDataType.STRING)
                    .example("Financial Analyst")
                    .aliases("title", "role", "position", "designation").build(),
            field("department", "Department", ImportDataType.REFERENCE)
                    .example("Finance")
                    .notes("Name or code of a department in your organisation.")
                    .aliases("dept", "department name", "cost centre", "org unit", "team").build(),
            field("manager", "Manager", ImportDataType.REFERENCE)
                    .example("")
                    .notes("Employee number, email or full name of another employee.")
                    .aliases("reports to", "line manager", "supervisor", "manager email").build(),
            enumField("status", "Status", EmployeeStatus.class)
                    .example("ACTIVE")
                    .notes("Defaults to ONBOARDING when blank.")
                    .aliases("employment status", "state", "employee status").build(),
            field("hireDate", "Hire date", ImportDataType.DATE)
                    .example("2023-04-01")
                    .notes("YYYY-MM-DD. DD/MM/YYYY is also accepted.")
                    .aliases("start date", "joined", "date joined", "employment start",
                            "hire date").build(),
            field("terminationDate", "Termination date", ImportDataType.DATE)
                    .example("")
                    .notes("YYYY-MM-DD. Leave blank for current employees.")
                    .aliases("end date", "leaving date", "exit date", "last day").build(),
            field("notes", "Notes", ImportDataType.TEXT)
                    .example("")
                    .aliases("comments", "remarks", "details").build()
    );

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final ImportReferenceResolver referenceResolver;
    private final ImportBeanValidator beanValidator;

    public EmployeeImportHandler(EmployeeService employeeService,
                                 EmployeeRepository employeeRepository,
                                 ImportReferenceResolver referenceResolver,
                                 ImportBeanValidator beanValidator) {
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.referenceResolver = referenceResolver;
        this.beanValidator = beanValidator;
    }

    @Override
    public ImportEntityType entityType() {
        return ImportEntityType.EMPLOYEES;
    }

    @Override
    public List<ImportFieldDescriptor> fields() {
        return FIELDS;
    }

    @Override
    public ImportRunner runner(Organisation organisation, ImportOptions options) {
        return new Runner(organisation, options);
    }

    private final class Runner extends AbstractImportRunner<EmployeeDto> {

        private final ImportReferenceResolver.Refs refs;
        private final Map<String, UUID> byNumber = new LinkedHashMap<>();
        private final Map<String, UUID> byEmail = new LinkedHashMap<>();

        private Runner(Organisation organisation, ImportOptions options) {
            super(options);
            this.refs = referenceResolver.open(organisation, options);
            for (Employee employee : employeeRepository.findByOrganisationAndDeletedAtIsNull(organisation)) {
                if (employee.getEmployeeNumber() != null && !employee.getEmployeeNumber().isBlank()) {
                    byNumber.putIfAbsent(key(employee.getEmployeeNumber()), employee.getId());
                }
                if (employee.getEmail() != null && !employee.getEmail().isBlank()) {
                    byEmail.putIfAbsent(key(employee.getEmail()), employee.getId());
                }
            }
        }

        @Override
        protected EmployeeDto build(ImportRow row) {
            EmployeeDto dto = EmployeeDto.builder()
                    .employeeNumber(row.string("employeeNumber"))
                    .firstName(row.requiredString("firstName"))
                    .lastName(row.requiredString("lastName"))
                    .email(row.string("email"))
                    .phone(row.string("phone"))
                    .jobTitle(row.string("jobTitle"))
                    .departmentId(refs.department(row.string("department"), "department"))
                    .managerId(refs.employee(row.string("manager"), "manager"))
                    .status(row.enumValue(EmployeeStatus.class, "status"))
                    .hireDate(row.date("hireDate"))
                    .terminationDate(row.date("terminationDate"))
                    .notes(row.string("notes"))
                    .build();

            if (dto.getTerminationDate() != null && dto.getHireDate() != null
                    && dto.getTerminationDate().isBefore(dto.getHireDate())) {
                throw new FieldValidationException("terminationDate",
                        "is before the hire date (" + dto.getHireDate() + ")");
            }
            beanValidator.validateForCreate(dto);
            return dto;
        }

        @Override
        protected UUID findExisting(ImportRow row, EmployeeDto payload) {
            if (payload.getEmployeeNumber() != null && !payload.getEmployeeNumber().isBlank()) {
                UUID match = byNumber.get(key(payload.getEmployeeNumber()));
                if (match != null) return match;
            }
            if (payload.getEmail() != null && !payload.getEmail().isBlank()) {
                return byEmail.get(key(payload.getEmail()));
            }
            return null;
        }

        @Override
        protected void create(EmployeeDto payload) {
            EmployeeDto created = employeeService.create(payload);
            if (payload.getEmployeeNumber() != null && !payload.getEmployeeNumber().isBlank()) {
                byNumber.put(key(payload.getEmployeeNumber()), created.getId());
            }
            if (payload.getEmail() != null && !payload.getEmail().isBlank()) {
                byEmail.put(key(payload.getEmail()), created.getId());
            }
        }

        @Override
        protected void update(UUID id, EmployeeDto payload) {
            employeeService.update(id, payload);
        }

        @Override
        protected String describe() {
            return "employee";
        }

        @Override
        protected String naturalKey(ImportRow row, EmployeeDto payload) {
            return payload.getEmployeeNumber() != null && !payload.getEmployeeNumber().isBlank()
                    ? "employee number '" + payload.getEmployeeNumber() + "'"
                    : "email '" + payload.getEmail() + "'";
        }

        private String key(String value) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
