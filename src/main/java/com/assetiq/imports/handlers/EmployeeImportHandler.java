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
import com.assetiq.imports.ImportRunReport;
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
                    .aliases("employee id", "employee code", "employee no", "employee no.",
                            "employee #", "emp id", "emp no", "staff id", "staff no",
                            "staff #", "staff number", "payroll number",
                            "personnel number", "person number", "hr id",
                            "badge number").build(),
            field("firstName", "First name", ImportDataType.STRING).required()
                    .example("Ama")
                    .aliases("given name", "given names", "forename", "first",
                            "name (first)").build(),
            field("lastName", "Last name", ImportDataType.STRING).required()
                    .example("Mensah")
                    .aliases("surname", "family name", "family", "last", "lastname",
                            "name (last)").build(),
            field("email", "Email", ImportDataType.EMAIL)
                    .example("ama.mensah@example.com")
                    .notes("Used to match rows to existing people when there is no employee number.")
                    .aliases("email address", "email id", "work email", "office email",
                            "primary email", "company email", "e-mail", "e mail").build(),
            field("phone", "Phone", ImportDataType.STRING)
                    .example("+233201234567")
                    .aliases("telephone", "mobile", "mobile number", "phone number", "phone no",
                            "phone no.", "phone #", "contact number", "cell",
                            "cell phone").build(),
            field("jobTitle", "Job title", ImportDataType.STRING)
                    .example("Financial Analyst")
                    .aliases("title", "role", "job role", "position", "job position", "post",
                            "designation").build(),
            field("department", "Department", ImportDataType.REFERENCE)
                    .example("Finance")
                    .notes("Name or code of a department in your organisation.")
                    .aliases("dept", "department name", "department code", "cost centre",
                            "cost center", "org unit", "division", "section",
                            "team").build(),
            field("manager", "Manager", ImportDataType.REFERENCE)
                    .example("")
                    .notes("Employee number, email or full name of another employee.")
                    .aliases("reports to", "reports to email", "line manager", "manager name",
                            "supervisor", "supervisor name", "manager email").build(),
            enumField("status", "Status", EmployeeStatus.class)
                    .example("ACTIVE")
                    .notes("Defaults to ONBOARDING when blank.")
                    .aliases("employment status", "employee status", "employee state",
                            "state").build(),
            field("hireDate", "Hire date", ImportDataType.DATE)
                    .example("2023-04-01")
                    .notes("YYYY-MM-DD. DD/MM/YYYY is also accepted.")
                    .aliases("start date", "joined", "date joined", "joining date",
                            "date of joining", "date hired", "date of hire",
                            "employment start", "commencement date", "hire date").build(),
            field("terminationDate", "Termination date", ImportDataType.DATE)
                    .example("")
                    .notes("YYYY-MM-DD. Leave blank for current employees.")
                    .aliases("end date", "leaving date", "date left", "exit date", "last day",
                            "separation date", "resignation date",
                            "end of employment").build(),
            field("notes", "Notes", ImportDataType.TEXT)
                    .example("")
                    .aliases("comments", "comment", "remarks", "details",
                            "additional info").build()
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
    public ImportRunner runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
        return new Runner(organisation, options, report);
    }

    private final class Runner extends AbstractImportRunner<EmployeeDto> {

        private final ImportReferenceResolver.Refs refs;
        private final Map<String, UUID> byNumber = new LinkedHashMap<>();
        private final Map<String, UUID> byEmail = new LinkedHashMap<>();

        private Runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
            super(options);
            this.refs = referenceResolver.open(organisation, options, report);
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
