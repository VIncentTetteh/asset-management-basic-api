package com.assetiq.imports.handlers;

import com.assetiq.dto.SoftwareLicenseDto;
import com.assetiq.enums.LicenseStatus;
import com.assetiq.enums.LicenseType;
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
import com.assetiq.models.Organisation;
import com.assetiq.models.SoftwareLicense;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.SoftwareLicenseService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.assetiq.imports.ImportFieldDescriptor.enumField;
import static com.assetiq.imports.ImportFieldDescriptor.field;

/**
 * Software licences. Duplicates are decided on vendor plus licence name: the same
 * product name from two vendors is two licences, and one vendor's product appearing
 * twice is one.
 *
 * <p>The licence key is deliberately absent from the descriptors, matching
 * {@link SoftwareLicenseDto}: a product key is a secret and does not travel through a
 * spreadsheet upload or a mapping preview.</p>
 */
@Component
public class SoftwareLicenceImportHandler implements ImportEntityHandler {

    private static final List<ImportFieldDescriptor> FIELDS = List.of(
            field("name", "Licence name", ImportDataType.STRING).required()
                    .example("Microsoft 365 E3")
                    .notes("'Product' maps to the product name column, not here: the two exist "
                            + "separately and guessing between them would be a coin toss.")
                    .aliases("license name", "licence name", "licence", "license",
                            "license title", "subscription", "software", "software name",
                            "agreement name", "title").build(),
            field("vendor", "Vendor", ImportDataType.STRING).required()
                    .example("Microsoft")
                    .aliases("publisher", "supplier", "vendor name", "software vendor", "licensor",
                            "manufacturer", "manufacturer name", "provider", "maker").build(),
            field("productName", "Product name", ImportDataType.STRING)
                    .example("Microsoft 365")
                    .aliases("product", "product title", "application", "app name", "sku").build(),
            field("version", "Version", ImportDataType.STRING)
                    .example("2024")
                    .aliases("release", "version number", "product version", "ver", "edition").build(),
            enumField("licenseType", "Licence type", LicenseType.class).required()
                    .example("SUBSCRIPTION")
                    .aliases("license type", "licence type", "type", "type of license",
                            "license category", "licence model", "license model",
                            "licensing").build(),
            enumField("status", "Status", LicenseStatus.class)
                    .example("ACTIVE")
                    .notes("Defaults to ACTIVE when blank.")
                    .aliases("license status", "licence status", "state").build(),
            field("totalSeats", "Total seats", ImportDataType.INTEGER)
                    .example("250")
                    .notes("Whole number of entitlements purchased.")
                    .aliases("seats", "seat count", "no of seats", "number of seats",
                            "licenses", "licences", "total licenses",
                            "number of licenses", "licensed quantity",
                            "quantity", "qty", "total qty", "qty purchased",
                            "entitlements", "purchased seats").build(),
            field("usedSeats", "Used seats", ImportDataType.INTEGER)
                    .example("198")
                    .notes("Whole number; must not exceed total seats.")
                    .aliases("assigned seats", "seats assigned", "seats used", "licenses used",
                            "allocated", "consumed", "installations", "deployed",
                            "in use", "used", "qty used").build(),
            field("purchaseCost", "Purchase cost", ImportDataType.DECIMAL)
                    .example("45000.00")
                    .notes("Numbers only; the currency goes in its own column.")
                    .aliases("cost", "price", "amount", "total cost", "initial cost",
                            "license cost", "licence cost", "purchase price",
                            "purchase amount", "spend").build(),
            field("annualRenewalCost", "Annual renewal cost", ImportDataType.DECIMAL)
                    .example("45000.00")
                    .aliases("renewal cost", "renewal amount", "annual cost", "annual fee",
                            "yearly cost", "yearly fee", "maintenance cost",
                            "subscription cost", "subscription fee", "arr").build(),
            field("currency", "Currency", ImportDataType.STRING)
                    .example("GHS")
                    .notes("Three-letter ISO 4217 code, e.g. GHS, USD, GBP.")
                    .aliases("ccy", "currency code", "iso currency").build(),
            field("purchaseDate", "Purchase date", ImportDataType.DATE)
                    .example("2024-01-15")
                    .notes("YYYY-MM-DD. DD/MM/YYYY is also accepted.")
                    .aliases("bought", "acquired", "date acquired", "date purchased",
                            "order date", "start date").build(),
            field("expiryDate", "Expiry date", ImportDataType.DATE)
                    .example("2025-01-14")
                    .notes("YYYY-MM-DD. Must not be before the purchase date.")
                    .aliases("expires", "expiry", "expires on", "expiration", "expiration date",
                            "end date", "valid until", "term end", "subscription end",
                            "support end").build(),
            field("renewalDate", "Renewal date", ImportDataType.DATE)
                    .example("2025-01-01")
                    .aliases("renews", "renew on", "next renewal", "next renewal date",
                            "renewal due").build(),
            field("autoRenew", "Auto renew", ImportDataType.BOOLEAN)
                    .example("yes")
                    .notes("yes / no, true / false, 1 / 0.")
                    .aliases("auto renewal", "auto-renewal", "automatic renewal", "auto-renew",
                            "auto renews", "renews automatically").build(),
            field("licenseDocumentUrl", "Licence document URL", ImportDataType.STRING)
                    .example("")
                    .notes("An http or https link to the agreement.")
                    .aliases("document url", "doc url", "license url", "agreement url",
                            "agreement link", "contract link", "document link").build(),
            field("asset", "Linked asset", ImportDataType.REFERENCE)
                    .example("")
                    .notes("Asset tag, serial number or name of an asset this licence is tied to.")
                    .aliases("asset tag", "asset name", "serial number", "assigned asset",
                            "device", "installed on", "host", "machine").build(),
            field("notes", "Notes", ImportDataType.TEXT)
                    .example("")
                    .aliases("comments", "comment", "remarks", "details").build()
    );

    private final SoftwareLicenseService licenseService;
    private final SoftwareLicenseRepository licenseRepository;
    private final ImportReferenceResolver referenceResolver;
    private final ImportBeanValidator beanValidator;

    public SoftwareLicenceImportHandler(SoftwareLicenseService licenseService,
                                        SoftwareLicenseRepository licenseRepository,
                                        ImportReferenceResolver referenceResolver,
                                        ImportBeanValidator beanValidator) {
        this.licenseService = licenseService;
        this.licenseRepository = licenseRepository;
        this.referenceResolver = referenceResolver;
        this.beanValidator = beanValidator;
    }

    @Override
    public ImportEntityType entityType() {
        return ImportEntityType.SOFTWARE_LICENCES;
    }

    @Override
    public List<ImportFieldDescriptor> fields() {
        return FIELDS;
    }

    @Override
    public ImportRunner runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
        return new Runner(organisation, options, report);
    }

    private final class Runner extends AbstractImportRunner<SoftwareLicenseDto> {

        private final ImportReferenceResolver.Refs refs;
        private final Map<String, UUID> byVendorAndName = new LinkedHashMap<>();

        private Runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
            super(options);
            this.refs = referenceResolver.open(organisation, options, report);
            for (SoftwareLicense licence : licenseRepository.findByOrganisationAndDeletedAtIsNull(organisation)) {
                byVendorAndName.putIfAbsent(key(licence.getVendor(), licence.getName()), licence.getId());
            }
        }

        @Override
        protected SoftwareLicenseDto build(ImportRow row) {
            SoftwareLicenseDto dto = new SoftwareLicenseDto();
            dto.setName(row.requiredString("name"));
            dto.setVendor(row.requiredString("vendor"));
            dto.setProductName(row.string("productName"));
            dto.setVersion(row.string("version"));
            dto.setLicenseType(row.enumValue(LicenseType.class, "licenseType"));
            if (dto.getLicenseType() == null) {
                throw new FieldValidationException("licenseType", "is required but was blank");
            }
            dto.setStatus(row.enumValue(LicenseStatus.class, "status"));
            dto.setTotalSeats(row.integer("totalSeats"));
            dto.setUsedSeats(row.integer("usedSeats"));
            dto.setPurchaseCost(row.decimal("purchaseCost"));
            dto.setAnnualRenewalCost(row.decimal("annualRenewalCost"));
            String currency = row.string("currency");
            dto.setCurrency(currency == null ? null : currency.toUpperCase(Locale.ROOT));
            dto.setPurchaseDate(row.date("purchaseDate"));
            dto.setExpiryDate(row.date("expiryDate"));
            dto.setRenewalDate(row.date("renewalDate"));
            dto.setAutoRenew(row.bool("autoRenew"));
            dto.setLicenseDocumentUrl(row.string("licenseDocumentUrl"));
            dto.setNotes(row.string("notes"));
            dto.setAssetId(refs.asset(row.string("asset"), "asset"));

            if (dto.getTotalSeats() != null && dto.getUsedSeats() != null
                    && dto.getUsedSeats() > dto.getTotalSeats()) {
                throw new FieldValidationException("usedSeats",
                        "is " + dto.getUsedSeats() + ", which is more than the "
                                + dto.getTotalSeats() + " total seats on this licence");
            }
            if (dto.getExpiryDate() != null && dto.getPurchaseDate() != null
                    && dto.getExpiryDate().isBefore(dto.getPurchaseDate())) {
                throw new FieldValidationException("expiryDate",
                        "is before the purchase date (" + dto.getPurchaseDate() + ")");
            }
            beanValidator.validateForCreate(dto);
            return dto;
        }

        @Override
        protected UUID findExisting(ImportRow row, SoftwareLicenseDto payload) {
            return byVendorAndName.get(key(payload.getVendor(), payload.getName()));
        }

        @Override
        protected void create(SoftwareLicenseDto payload) {
            SoftwareLicenseDto created = licenseService.create(payload);
            byVendorAndName.put(key(payload.getVendor(), payload.getName()), created.getId());
        }

        @Override
        protected void update(UUID id, SoftwareLicenseDto payload) {
            licenseService.patch(id, payload);
        }

        @Override
        protected String describe() {
            return "software licence";
        }

        @Override
        protected String naturalKey(ImportRow row, SoftwareLicenseDto payload) {
            return "'" + payload.getName() + "' from '" + payload.getVendor() + "'";
        }

        private String key(String vendor, String name) {
            return (vendor == null ? "" : vendor.trim().toLowerCase(Locale.ROOT))
                    + "\u0000" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
        }
    }
}
