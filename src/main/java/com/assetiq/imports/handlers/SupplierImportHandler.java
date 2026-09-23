package com.assetiq.imports.handlers;

import com.assetiq.dto.SupplierDto;
import com.assetiq.enums.SupplierStatus;
import com.assetiq.imports.AbstractImportRunner;
import com.assetiq.imports.ImportBeanValidator;
import com.assetiq.imports.ImportDataType;
import com.assetiq.imports.ImportEntityHandler;
import com.assetiq.imports.ImportEntityType;
import com.assetiq.imports.ImportFieldDescriptor;
import com.assetiq.imports.ImportOptions;
import com.assetiq.imports.ImportRow;
import com.assetiq.imports.ImportRunReport;
import com.assetiq.models.Organisation;
import com.assetiq.models.Supplier;
import com.assetiq.repositories.SupplierRepository;
import com.assetiq.services.SupplierService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.assetiq.imports.ImportFieldDescriptor.enumField;
import static com.assetiq.imports.ImportFieldDescriptor.field;

/** Suppliers. Duplicates are decided on supplier name, case-insensitively. */
@Component
public class SupplierImportHandler implements ImportEntityHandler {

    private static final List<ImportFieldDescriptor> FIELDS = List.of(
            field("name", "Supplier name", ImportDataType.STRING).required()
                    .example("Acme Technologies Ltd")
                    .notes("Must be unique within your organisation.")
                    .aliases("vendor", "vendor name", "supplier", "name", "company", "company name",
                            "business name", "trading name", "organisation", "organization",
                            "payee", "manufacturer name").build(),
            field("registrationNumber", "Registration number", ImportDataType.STRING)
                    .example("CS123456789")
                    .aliases("company registration", "reg no", "reg no.", "reg number",
                            "registration no", "registration no.", "company number",
                            "incorporation number", "crn",
                            "business registration number", "duns").build(),
            field("contactPerson", "Contact person", ImportDataType.STRING)
                    .example("Ama Mensah")
                    .aliases("contact", "contact name", "contact full name", "primary contact",
                            "point of contact", "poc", "account manager", "salesperson",
                            "rep", "representative").build(),
            field("email", "Email", ImportDataType.EMAIL)
                    .example("sales@acmetech.example")
                    .aliases("email address", "contact email", "primary email", "sales email",
                            "e-mail", "e mail").build(),
            field("phone", "Phone", ImportDataType.STRING)
                    .example("+233201234567")
                    .notes("Digits, optionally with a leading +. Spaces and dashes are accepted.")
                    .aliases("telephone", "telephone number", "phone number", "phone no",
                            "phone no.", "phone #", "contact phone", "contact number",
                            "office phone", "mobile", "tel").build(),
            field("address", "Address", ImportDataType.TEXT)
                    .example("12 Independence Ave, Accra")
                    .aliases("street address", "address line 1", "physical address",
                            "postal address", "office address", "location",
                            "billing address").build(),
            field("taxId", "Tax ID", ImportDataType.STRING)
                    .example("C0012345678")
                    .aliases("tin", "vat number", "vat reg no", "vat id", "tax number",
                            "tax id number", "tax identification number", "gst number",
                            "ein").build(),
            enumField("status", "Status", SupplierStatus.class)
                    .example("ACTIVE")
                    .notes("Defaults to ACTIVE when blank.")
                    .aliases("supplier status", "vendor status", "state").build()
    );

    private final SupplierService supplierService;
    private final SupplierRepository supplierRepository;
    private final ImportBeanValidator beanValidator;

    public SupplierImportHandler(SupplierService supplierService,
                                 SupplierRepository supplierRepository,
                                 ImportBeanValidator beanValidator) {
        this.supplierService = supplierService;
        this.supplierRepository = supplierRepository;
        this.beanValidator = beanValidator;
    }

    @Override
    public ImportEntityType entityType() {
        return ImportEntityType.SUPPLIERS;
    }

    @Override
    public List<ImportFieldDescriptor> fields() {
        return FIELDS;
    }

    @Override
    public ImportRunner runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
        return new Runner(organisation, options, report);
    }

    private final class Runner extends AbstractImportRunner<SupplierDto> {

        private final Map<String, UUID> byName = new LinkedHashMap<>();

        private Runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
            super(options);
            for (Supplier supplier : supplierRepository.findByOrganisationAndDeletedAtIsNull(organisation)) {
                if (supplier.getName() != null) {
                    byName.putIfAbsent(supplier.getName().trim().toLowerCase(Locale.ROOT), supplier.getId());
                }
            }
        }

        @Override
        protected SupplierDto build(ImportRow row) {
            SupplierDto dto = new SupplierDto();
            dto.setName(row.requiredString("name"));
            dto.setRegistrationNumber(row.string("registrationNumber"));
            dto.setContactPerson(row.string("contactPerson"));
            dto.setEmail(row.string("email"));
            dto.setPhone(row.string("phone"));
            dto.setAddress(row.string("address"));
            dto.setTaxId(row.string("taxId"));
            dto.setStatus(row.enumValue(SupplierStatus.class, "status"));
            beanValidator.validateForCreate(dto);
            return dto;
        }

        @Override
        protected UUID findExisting(ImportRow row, SupplierDto payload) {
            return byName.get(payload.getName().trim().toLowerCase(Locale.ROOT));
        }

        @Override
        protected void create(SupplierDto payload) {
            SupplierDto created = supplierService.createSupplier(payload);
            // Register immediately so a second identical row in the same file is seen
            // as the duplicate it is, rather than creating a twin.
            byName.put(payload.getName().trim().toLowerCase(Locale.ROOT), created.getId());
        }

        @Override
        protected void update(UUID id, SupplierDto payload) {
            supplierService.patchSupplier(id, payload);
        }

        @Override
        protected String describe() {
            return "supplier";
        }

        @Override
        protected String naturalKey(ImportRow row, SupplierDto payload) {
            return "supplier name '" + payload.getName() + "'";
        }
    }
}
