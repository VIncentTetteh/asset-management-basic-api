package com.assetiq.imports;

import com.assetiq.dto.CategoryDto;
import com.assetiq.dto.DepartmentDto;
import com.assetiq.dto.LocationDto;
import com.assetiq.dto.SupplierDto;
import com.assetiq.models.Asset;
import com.assetiq.models.BaseEntity;
import com.assetiq.models.Category;
import com.assetiq.models.Department;
import com.assetiq.models.Employee;
import com.assetiq.models.Location;
import com.assetiq.models.Organisation;
import com.assetiq.models.Supplier;
import com.assetiq.models.User;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.CategoryRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.EmployeeRepository;
import com.assetiq.repositories.LocationRepository;
import com.assetiq.repositories.SupplierRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.CategoryService;
import com.assetiq.services.DepartmentService;
import com.assetiq.services.LocationService;
import com.assetiq.services.SupplierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Resolves the human-readable names in a sheet to record ids, for one tenant, for the
 * duration of one import run.
 *
 * <p>Nobody migrating from another platform has AssetIQ UUIDs, so every cross-record
 * column in every template is a name. Each lookup table is loaded once per run and held
 * in the {@link Refs} handle — an import of 3000 assets would otherwise issue 15000
 * queries resolving the same dozen categories.</p>
 *
 * <p>The handle is created per run and never shared: every table is built from a
 * repository call scoped to the tenant's {@link Organisation}, so a name that exists in
 * another organisation simply is not there, and anything created is created against that
 * same organisation.</p>
 *
 * <h2>When a name is not found</h2>
 * <p>Three different answers, and the difference matters:</p>
 * <ul>
 *   <li><b>A category, location, supplier or department</b>, with reference creation on
 *       (the wizard's default): create it, remember it by name for the rest of the file,
 *       and report it. Bounded by
 *       {@link ImportOptions#MAX_CREATED_PER_REFERENCE_TYPE} per type per run — a file
 *       that would exceed it is not a migration, it is a mistake, and it stops and says
 *       so rather than manufacturing hundreds of records unnoticed.</li>
 *   <li><b>The same, with reference creation off</b>: the row fails. The caller asked for
 *       strictness explicitly and gets it.</li>
 *   <li><b>A user, employee or asset</b>: never created — an account is an identity and
 *       inventing one from a spreadsheet cell is not acceptable — but the row is not
 *       lost over it either. The field is left blank and a note names the cell, so a
 *       3000-row migration is not held hostage by a leaver still listed as a custodian
 *       in the old system.</li>
 * </ul>
 */
@Component
public class ImportReferenceResolver {

    private static final Logger log = LoggerFactory.getLogger(ImportReferenceResolver.class);

    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SupplierRepository supplierRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AssetRepository assetRepository;
    private final CategoryService categoryService;
    private final LocationService locationService;
    private final SupplierService supplierService;
    private final DepartmentService departmentService;

    public ImportReferenceResolver(CategoryRepository categoryRepository,
                                   LocationRepository locationRepository,
                                   SupplierRepository supplierRepository,
                                   DepartmentRepository departmentRepository,
                                   UserRepository userRepository,
                                   EmployeeRepository employeeRepository,
                                   AssetRepository assetRepository,
                                   CategoryService categoryService,
                                   LocationService locationService,
                                   SupplierService supplierService,
                                   DepartmentService departmentService) {
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.supplierRepository = supplierRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.assetRepository = assetRepository;
        this.categoryService = categoryService;
        this.locationService = locationService;
        this.supplierService = supplierService;
        this.departmentService = departmentService;
    }

    public Refs open(Organisation organisation, ImportOptions options) {
        return open(organisation, options, new ImportRunReport());
    }

    public Refs open(Organisation organisation, ImportOptions options, ImportRunReport report) {
        return new Refs(organisation, options, report);
    }

    /** Per-run, per-tenant lookup tables. Lazily loaded: a sheet with no supplier
     *  column never pays for the supplier table. */
    public final class Refs {

        private final Organisation organisation;
        private final ImportOptions options;
        private final ImportRunReport report;
        private final Map<String, Map<String, UUID>> tables = new HashMap<>();
        /** Names a dry run decided it would create, so the same name is only counted once. */
        private final Map<String, Set<String>> simulated = new HashMap<>();

        private Refs(Organisation organisation, ImportOptions options, ImportRunReport report) {
            this.organisation = organisation;
            this.options = options;
            this.report = report;
        }

        public UUID category(String name, String field) {
            return resolve("category", name, field,
                    () -> index(categoryRepository.findByOrganisationAndDeletedAtIsNull(organisation),
                            e -> ((Category) e).getName()),
                    this::createCategory);
        }

        public UUID location(String name, String field) {
            return resolve("location", name, field,
                    () -> index(locationRepository.findByOrganisationAndDeletedAtIsNull(organisation),
                            e -> ((Location) e).getName()),
                    this::createLocation);
        }

        public UUID supplier(String name, String field) {
            return resolve("supplier", name, field,
                    () -> index(supplierRepository.findByOrganisationAndDeletedAtIsNull(organisation),
                            e -> ((Supplier) e).getName()),
                    this::createSupplier);
        }

        /** Departments match on name or department code. */
        public UUID department(String name, String field) {
            return resolve("department", name, field, () -> {
                Map<String, UUID> index = new LinkedHashMap<>();
                Collection<Department> departments =
                        departmentRepository.findAllByOrganisationAndDeletedAtIsNull(organisation);
                departments.stream().filter(d -> d.getName() != null)
                        .forEach(d -> index.putIfAbsent(key(d.getName()), d.getId()));
                departments.stream().filter(d -> d.getDepartmentCode() != null && !d.getDepartmentCode().isBlank())
                        .forEach(d -> index.putIfAbsent(key(d.getDepartmentCode()), d.getId()));
                return index;
            }, this::createDepartment);
        }

        /** Users match on email or employee number. Never auto-created: an account is
         *  an identity, and inventing one from a spreadsheet cell is not acceptable. */
        public UUID user(String value, String field) {
            return resolve("user", value, field, () -> {
                Map<String, UUID> index = new LinkedHashMap<>();
                Collection<User> users = userRepository.findByOrganisationAndDeletedAtIsNull(organisation);
                users.stream().filter(u -> u.getEmail() != null)
                        .forEach(u -> index.putIfAbsent(key(u.getEmail()), u.getId()));
                users.stream().filter(u -> u.getEmployeeId() != null && !u.getEmployeeId().isBlank())
                        .forEach(u -> index.putIfAbsent(key(u.getEmployeeId()), u.getId()));
                return index;
            }, null);
        }

        /** Employees match on employee number, email, or "First Last". */
        public UUID employee(String value, String field) {
            return resolve("employee", value, field, () -> {
                Map<String, UUID> index = new LinkedHashMap<>();
                Collection<Employee> employees =
                        employeeRepository.findByOrganisationAndDeletedAtIsNull(organisation);
                employees.stream().filter(e -> e.getEmployeeNumber() != null && !e.getEmployeeNumber().isBlank())
                        .forEach(e -> index.putIfAbsent(key(e.getEmployeeNumber()), e.getId()));
                employees.stream().filter(e -> e.getEmail() != null)
                        .forEach(e -> index.putIfAbsent(key(e.getEmail()), e.getId()));
                employees.forEach(e -> {
                    String full = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                            + (e.getLastName() == null ? "" : e.getLastName())).trim();
                    if (!full.isEmpty()) index.putIfAbsent(key(full), e.getId());
                });
                return index;
            }, null);
        }

        /** Assets match on asset tag, then serial number, then name. */
        public UUID asset(String value, String field) {
            return resolve("asset", value, field, () -> {
                Map<String, UUID> index = new LinkedHashMap<>();
                Collection<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(organisation);
                assets.stream().filter(a -> a.getAssetTag() != null && !a.getAssetTag().isBlank())
                        .forEach(a -> index.putIfAbsent(key(a.getAssetTag()), a.getId()));
                assets.stream().filter(a -> a.getSerialNumber() != null && !a.getSerialNumber().isBlank())
                        .forEach(a -> index.putIfAbsent(key(a.getSerialNumber()), a.getId()));
                assets.stream().filter(a -> a.getName() != null)
                        .forEach(a -> index.putIfAbsent(key(a.getName()), a.getId()));
                return index;
            }, null);
        }

        // ── Plumbing ──────────────────────────────────────────────────────────

        private UUID resolve(String table,
                             String rawValue,
                             String field,
                             java.util.function.Supplier<Map<String, UUID>> loader,
                             Function<String, UUID> creator) {
            if (rawValue == null || rawValue.isBlank()) return null;
            Map<String, UUID> index = tables.computeIfAbsent(table, t -> loader.get());
            UUID id = index.get(key(rawValue));
            if (id != null) return id;

            String name = rawValue.trim();

            if (creator == null) {
                // A user, employee or asset we do not have. Never invented, never fatal:
                // see the class comment.
                report.note(field, null, name,
                        "No " + table + " named '" + name + "' exists in your organisation,"
                                + " so this row was imported without it. Add the " + table
                                + " and re-import to fill it in.");
                return null;
            }

            if (!options.createMissingReferences()) {
                throw new FieldValidationException(field,
                        "names '" + name + "', which does not exist in your organisation."
                                + " Create it first, or re-run the import with"
                                + " 'create missing referenced records' turned on.");
            }

            if (alreadyCreated(table) >= ImportOptions.MAX_CREATED_PER_REFERENCE_TYPE) {
                throw new FieldValidationException(field,
                        "would be the " + (ImportOptions.MAX_CREATED_PER_REFERENCE_TYPE + 1) + "th new "
                                + table + " this file creates, past the limit of "
                                + ImportOptions.MAX_CREATED_PER_REFERENCE_TYPE + "."
                                + " Check the column is the right one, tidy the values, or create the"
                                + " remaining " + table + " records first and re-import.");
            }

            if (options.dryRun()) {
                // A dry run must not write. The name is still counted and reported so the
                // preview can say exactly what a real run would create, and so the bound
                // is enforced identically in both.
                simulated.computeIfAbsent(table, t -> new HashSet<>()).add(key(name));
                report.referenceCreated(table, name);
                return null;
            }

            UUID created = creator.apply(name);
            index.put(key(name), created);
            report.referenceCreated(table, name);
            log.info("Import: created {} '{}' for org={}", table, name, organisation.getId());
            return created;
        }

        /**
         * How many of this type the run has committed to creating — really created, or
         * decided it would on a dry run. Counted the same way in both so the preview
         * cannot promise an import the bound would later refuse.
         */
        private int alreadyCreated(String table) {
            return options.dryRun()
                    ? simulated.getOrDefault(table, Set.of()).size()
                    : report.createdCount(table);
        }

        private UUID createCategory(String name) {
            CategoryDto dto = new CategoryDto();
            dto.setName(name);
            return categoryService.createCategory(dto, organisation.getId()).getId();
        }

        private UUID createLocation(String name) {
            LocationDto dto = new LocationDto();
            dto.setName(name);
            return locationService.createLocation(dto, organisation.getId()).getId();
        }

        private UUID createSupplier(String name) {
            SupplierDto dto = new SupplierDto();
            dto.setName(name);
            return supplierService.createSupplier(dto).getId();
        }

        private UUID createDepartment(String name) {
            DepartmentDto dto = new DepartmentDto();
            dto.setName(name);
            return departmentService.create(dto).getId();
        }

        private Map<String, UUID> index(Collection<? extends BaseEntity> entities,
                                        Function<BaseEntity, String> nameOf) {
            Map<String, UUID> index = new LinkedHashMap<>();
            for (BaseEntity entity : entities) {
                String name = nameOf.apply(entity);
                if (name == null || name.isBlank()) continue;
                index.putIfAbsent(key(name), entity.getId());
            }
            return index;
        }
    }

    static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
