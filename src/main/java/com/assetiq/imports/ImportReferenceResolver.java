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
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
 * another organisation simply is not there.</p>
 */
@Component
public class ImportReferenceResolver {

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
        return new Refs(organisation, options);
    }

    /** Per-run, per-tenant lookup tables. Lazily loaded: a sheet with no supplier
     *  column never pays for the supplier table. */
    public final class Refs {

        private final Organisation organisation;
        private final ImportOptions options;
        private final Map<String, Map<String, UUID>> tables = new HashMap<>();

        private Refs(Organisation organisation, ImportOptions options) {
            this.organisation = organisation;
            this.options = options;
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

            if (creator != null && options.createMissingReferences() && !options.dryRun()) {
                UUID created = creator.apply(rawValue.trim());
                index.put(key(rawValue), created);
                return created;
            }
            if (creator != null && options.createMissingReferences() && options.dryRun()) {
                // A dry run must not write; the row is still reported as valid because a
                // real run would create the record.
                return null;
            }
            throw new FieldValidationException(field,
                    "names '" + rawValue.trim() + "', which does not exist in your organisation."
                            + " Create it first, or re-run the import with"
                            + " 'create missing referenced records' turned on.");
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
