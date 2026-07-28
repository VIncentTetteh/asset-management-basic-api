package com.assetiq.config;

import com.assetiq.models.Asset;
import com.assetiq.models.Category;
import com.assetiq.models.Department;
import com.assetiq.models.Location;
import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.AssetType;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import com.assetiq.enums.ProcurementType;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.CategoryRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.LocationRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dev-only demo estate seeder. Runs after {@link DevDataSeeder} (which creates
 * the "Kwabenya Depot Ltd" tenant, departments, ADMIN role and the primary
 * user) and populates a believable Ghanaian asset estate so the dashboard,
 * analytics, reports and depreciation views show substance instead of zeros
 * out of the box.
 *
 * <p>Without this, a fresh instance logs in to an empty org — every KPI reads
 * 0 and every chart is blank, which makes the product look hollow in a
 * walkthrough. This seeder fixes that: ~48 assets across categories,
 * departments, locations, statuses and purchase dates (so straight-line
 * depreciation produces a real Net Book Value), plus maintenance records with
 * a spread of overdue / upcoming due dates that light up the maintenance
 * alerts panel.
 *
 * <p>Idempotent: if the demo tenant already has any asset, this seeder is a
 * no-op. Activated only when {@code spring.profiles.active} includes "dev".
 * Amounts are in GHS to match the tenant's billing currency.
 */
@Component
@Order(2)
@Profile("dev")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String ORG_NAME = "Kwabenya Depot Ltd";
    private static final String PRIMARY_USER_EMAIL = "ama.boateng@kwabenya.com.gh";
    private static final String CURRENCY = "GHS";

    // Departments seeded by DevDataSeeder — looked up by name here.
    private static final List<String> DEPARTMENT_NAMES = List.of(
            "Operations", "Finance & Compliance", "Kumasi Branch", "Tamale Branch", "IT & Security");

    private final OrganisationRepository organisationRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final AssetRepository assetRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;

    public DemoDataSeeder(OrganisationRepository organisationRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            LocationRepository locationRepository,
            AssetRepository assetRepository,
            MaintenanceRecordRepository maintenanceRecordRepository) {
        this.organisationRepository = organisationRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.assetRepository = assetRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<Organisation> orgOpt =
                organisationRepository.findByNameIgnoreCaseAndDeletedAtIsNull(ORG_NAME);
        if (orgOpt.isEmpty()) {
            log.warn("[DEMO SEED] Org '{}' not found — DevDataSeeder must run first. Skipping.", ORG_NAME);
            return;
        }
        Organisation org = orgOpt.get();

        if (!assetRepository.findAllByOrganisationAndDeletedAtIsNull(org).isEmpty()) {
            log.info("[DEMO SEED] Demo estate already present for '{}' — skipping.", ORG_NAME);
            return;
        }

        log.info("[DEMO SEED] Seeding demo asset estate for '{}'…", ORG_NAME);

        List<Department> departments = loadDepartments(org);
        User primaryUser = userRepository
                .findByEmailAndOrganisationId(PRIMARY_USER_EMAIL, org.getId())
                .orElse(null);

        List<Category> categories = seedCategories(org);
        List<Location> locations = seedLocations(org);

        List<Asset> assets = seedAssets(org, departments, locations, categories, primaryUser);
        seedMaintenance(org, assets);

        log.info("[DEMO SEED] Done — {} assets, {} categories, {} locations, maintenance seeded.",
                assets.size(), categories.size(), locations.size());
    }

    private List<Department> loadDepartments(Organisation org) {
        List<Department> found = new ArrayList<>();
        for (String name : DEPARTMENT_NAMES) {
            departmentRepository
                    .findByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(name, org)
                    .ifPresent(found::add);
        }
        return found;
    }

    private List<Category> seedCategories(Organisation org) {
        // name, prefix, warranty months, description
        Object[][] defs = {
                {"Laptops & Workstations", "LAP", 24, "End-user computing devices"},
                {"Servers & Storage", "SRV", 36, "Data-centre compute and storage"},
                {"Network Equipment", "NET", 24, "Switches, routers, firewalls, APs"},
                {"Vehicles", "VEH", 60, "Fleet and distribution vehicles"},
                {"Office Furniture", "FUR", 120, "Desks, chairs, cabinets"},
                {"Software Licenses", "SFT", 12, "Perpetual and subscription software"},
        };
        List<Category> out = new ArrayList<>();
        for (Object[] d : defs) {
            Category c = new Category();
            c.setName((String) d[0]);
            c.setAssetPrefixCode((String) d[1]);
            c.setDefaultWarrantyPeriodMonths((Integer) d[2]);
            c.setDescription((String) d[3]);
            c.setOrganisation(org);
            out.add(categoryRepository.save(c));
        }
        return out;
    }

    private List<Location> seedLocations(Organisation org) {
        // name, building, city, address
        Object[][] defs = {
                {"Accra Head Office", "Kwabenya Tower", "Accra", "12 Haatso Road, Kwabenya, Accra"},
                {"Kumasi Branch", "Adum Complex", "Kumasi", "Prempeh II Street, Adum, Kumasi"},
                {"Tamale Distribution Hub", "Northern Depot", "Tamale", "Bolgatanga Road, Tamale"},
        };
        List<Location> out = new ArrayList<>();
        for (Object[] d : defs) {
            Location l = new Location();
            l.setName((String) d[0]);
            l.setBuilding((String) d[1]);
            l.setCity((String) d[2]);
            l.setCountry("GH");
            l.setAddress((String) d[3]);
            l.setOrganisation(org);
            out.add(locationRepository.save(l));
        }
        return out;
    }

    /** Per-category asset template: type, manufacturer, model, base cost (GHS), useful-life months. */
    private record Template(AssetType type, String manufacturer, String model,
            long baseCost, int usefulLifeMonths, ProcurementType procurement) {
    }

    private List<Asset> seedAssets(Organisation org, List<Department> departments,
            List<Location> locations, List<Category> categories, User primaryUser) {

        // One template per seeded category (indices align with seedCategories order).
        Template[] templates = {
                new Template(AssetType.HARDWARE, "Dell", "Latitude 5540", 12_500, 36, ProcurementType.CAPEX),
                new Template(AssetType.HARDWARE, "HPE", "ProLiant DL380", 78_000, 60, ProcurementType.CAPEX),
                new Template(AssetType.HARDWARE, "Cisco", "Catalyst 9300", 21_000, 60, ProcurementType.CAPEX),
                new Template(AssetType.VEHICLE, "Toyota", "Hilux 2.4L", 320_000, 96, ProcurementType.CAPEX),
                new Template(AssetType.FURNITURE, "Orthopedic", "Exec Desk + Chair", 3_800, 120, ProcurementType.OPEX),
                new Template(AssetType.SOFTWARE, "Microsoft", "M365 E3 (seat pack)", 9_500, 12, ProcurementType.OPEX),
        };

        // Roughly how many of each category to create.
        int[] counts = {14, 4, 8, 5, 12, 5}; // = 48
        // Status distribution walked cyclically for a realistic spread.
        AssetStatus[] statusCycle = {
                AssetStatus.IN_USE, AssetStatus.IN_USE, AssetStatus.IN_USE, AssetStatus.IN_USE,
                AssetStatus.IN_USE, AssetStatus.IN_USE, AssetStatus.IN_STOCK, AssetStatus.RESERVED,
                AssetStatus.MAINTENANCE, AssetStatus.IN_USE, AssetStatus.UNDER_REPAIR, AssetStatus.IN_USE,
                AssetStatus.RETIRED, AssetStatus.IN_USE, AssetStatus.DISPOSED, AssetStatus.IN_USE,
        };
        AssetCondition[] conditionCycle = {
                AssetCondition.NEW, AssetCondition.EXCELLENT, AssetCondition.GOOD,
                AssetCondition.GOOD, AssetCondition.FAIR, AssetCondition.DAMAGED,
        };

        List<Asset> saved = new ArrayList<>();
        int seq = 0;
        for (int ci = 0; ci < templates.length; ci++) {
            Template t = templates[ci];
            Category category = categories.get(ci);
            String prefix = category.getAssetPrefixCode();
            for (int n = 0; n < counts[ci]; n++, seq++) {
                Asset a = new Asset();
                a.setName(t.manufacturer() + " " + t.model() + " #" + String.format("%02d", n + 1));
                a.setAssetTag(String.format("KDL-%s-%03d", prefix, n + 1));
                a.setSerialNumber(String.format("%s%06d", prefix, 100000 + seq * 37));
                a.setCategory(category);
                a.setAssetType(t.type());
                a.setManufacturer(t.manufacturer());
                a.setModel(t.model());
                a.setProcurementType(t.procurement());
                a.setCurrency(CURRENCY);

                // Purchase date spread from ~6 to ~60 months ago so depreciation varies.
                int monthsAgo = 6 + ((seq * 7) % 54);
                LocalDate purchaseDate = LocalDate.now().minusMonths(monthsAgo);
                a.setPurchaseDate(purchaseDate);

                // Cost varies +/- around the template base.
                long cost = t.baseCost() + (long) (t.baseCost() * 0.05) * (seq % 5);
                BigDecimal purchaseCost = BigDecimal.valueOf(cost);
                a.setPurchaseCost(purchaseCost);
                a.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
                a.setUsefulLifeMonths(t.usefulLifeMonths());
                BigDecimal residual = purchaseCost.multiply(BigDecimal.valueOf(0.10))
                        .setScale(2, RoundingMode.HALF_UP);
                a.setResidualValue(residual);
                a.setCurrentBookValue(straightLineNbv(purchaseCost, residual, t.usefulLifeMonths(), monthsAgo));
                a.setWarrantyExpiryDate(purchaseDate.plusMonths(
                        category.getDefaultWarrantyPeriodMonths() != null
                                ? category.getDefaultWarrantyPeriodMonths() : 12));

                a.setStatus(statusCycle[seq % statusCycle.length]);
                a.setCondition(conditionCycle[seq % conditionCycle.length]);
                a.setDepartment(departments.isEmpty() ? null : departments.get(seq % departments.size()));
                a.setLocation(locations.isEmpty() ? null : locations.get(seq % locations.size()));
                a.setCostCenter("CC-" + String.format("%03d", 100 + (seq % departments.size())));
                if (primaryUser != null && a.getStatus() == AssetStatus.IN_USE) {
                    a.setAssignedUser(primaryUser);
                }
                a.setOrganisation(org);
                saved.add(assetRepository.save(a));
            }
        }
        return saved;
    }

    private static BigDecimal straightLineNbv(BigDecimal cost, BigDecimal residual,
            int usefulLifeMonths, int monthsElapsed) {
        if (usefulLifeMonths <= 0) return cost;
        BigDecimal depreciable = cost.subtract(residual);
        if (depreciable.signum() <= 0) return cost;
        BigDecimal monthly = depreciable.divide(BigDecimal.valueOf(usefulLifeMonths), 2, RoundingMode.HALF_UP);
        BigDecimal accumulated = monthly.multiply(BigDecimal.valueOf(Math.min(monthsElapsed, usefulLifeMonths)));
        return cost.subtract(accumulated).max(residual).setScale(2, RoundingMode.HALF_UP);
    }

    private void seedMaintenance(Organisation org, List<Asset> assets) {
        if (assets.isEmpty()) return;
        LocalDate today = LocalDate.now();

        // (assetIndex, type, status, nextDueOffsetDays, costGhs, description)
        Object[][] defs = {
                {1, MaintenanceType.PREVENTIVE, MaintenanceStatus.SCHEDULED, -12, 1_800, "Quarterly server room service overdue"},
                {2, MaintenanceType.CORRECTIVE, MaintenanceStatus.IN_PROGRESS, -3, 950, "Switch fan replacement in progress"},
                {3, MaintenanceType.ROUTINE, MaintenanceStatus.SCHEDULED, 5, 600, "Fleet vehicle 5,000km service"},
                {4, MaintenanceType.PREVENTIVE, MaintenanceStatus.SCHEDULED, 18, 450, "UPS battery health check"},
                {5, MaintenanceType.EMERGENCY, MaintenanceStatus.SCHEDULED, 2, 2_400, "Generator fault — urgent callout"},
                {6, MaintenanceType.ROUTINE, MaintenanceStatus.COMPLETED, 90, 300, "Laptop fleet OS patch cycle"},
                {7, MaintenanceType.PREVENTIVE, MaintenanceStatus.SCHEDULED, 27, 750, "Air-conditioner filter change"},
        };

        for (Object[] d : defs) {
            int idx = (Integer) d[0];
            if (idx >= assets.size()) continue;
            MaintenanceRecord r = new MaintenanceRecord();
            r.setAsset(assets.get(idx));
            r.setMaintenanceType((MaintenanceType) d[1]);
            r.setStatus((MaintenanceStatus) d[2]);
            r.setNextDueDate(today.plusDays((Integer) d[3]));
            r.setScheduledDate(today.plusDays((Integer) d[3]));
            r.setCost(BigDecimal.valueOf((Integer) d[4]));
            r.setDescription((String) d[5]);
            r.setOrganisation(org);
            maintenanceRecordRepository.save(r);
        }
    }
}
