package com.assetiq.imports.handlers;

import com.assetiq.dto.ContractDto;
import com.assetiq.enums.ContractStatus;
import com.assetiq.enums.ContractType;
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
import com.assetiq.models.Contract;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.ContractRepository;
import com.assetiq.services.ContractService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.assetiq.imports.ImportFieldDescriptor.enumField;
import static com.assetiq.imports.ImportFieldDescriptor.field;

/**
 * Contracts. Duplicates are decided on contract number where the row has one, and
 * otherwise on title — two genuinely different agreements often share a title, so the
 * number wins whenever it is present.
 */
@Component
public class ContractImportHandler implements ImportEntityHandler {

    private static final List<ImportFieldDescriptor> FIELDS = List.of(
            field("title", "Title", ImportDataType.STRING).required()
                    .example("Datacentre maintenance 2025")
                    .aliases("contract title", "name", "contract name", "agreement",
                            "agreement name", "description").build(),
            field("contractNumber", "Contract number", ImportDataType.STRING)
                    .example("CTR-2025-0041")
                    .notes("Used to match rows to existing contracts.")
                    .aliases("contract no", "reference", "ref", "agreement number",
                            "contract id", "po number").build(),
            enumField("contractType", "Contract type", ContractType.class).required()
                    .example("MAINTENANCE")
                    .aliases("type", "agreement type", "category", "contract category").build(),
            enumField("status", "Status", ContractStatus.class)
                    .example("ACTIVE")
                    .notes("Defaults to DRAFT when blank.")
                    .aliases("contract status", "state").build(),
            field("supplier", "Supplier", ImportDataType.REFERENCE)
                    .example("Acme Technologies Ltd")
                    .notes("Name of a supplier in your organisation.")
                    .aliases("vendor", "counterparty", "supplier name", "vendor name",
                            "provider").build(),
            field("asset", "Linked asset", ImportDataType.REFERENCE)
                    .example("")
                    .notes("Asset tag, serial number or name of an asset this contract covers.")
                    .aliases("asset tag", "covered asset", "equipment", "device").build(),
            field("startDate", "Start date", ImportDataType.DATE).required()
                    .example("2025-01-01")
                    .notes("YYYY-MM-DD. DD/MM/YYYY is also accepted.")
                    .aliases("commencement", "effective date", "from", "valid from",
                            "term start").build(),
            field("endDate", "End date", ImportDataType.DATE).required()
                    .example("2025-12-31")
                    .notes("YYYY-MM-DD. Must not be before the start date.")
                    .aliases("expiry", "expiry date", "expiration", "to", "valid until",
                            "term end", "renewal date").build(),
            field("alertDaysBefore", "Alert days before expiry", ImportDataType.INTEGER)
                    .example("30")
                    .notes("Whole number of days. How far ahead of the end date to warn.")
                    .aliases("notice period", "reminder days", "alert days", "notify days").build(),
            field("value", "Contract value", ImportDataType.DECIMAL)
                    .example("120000.00")
                    .notes("Numbers only; the currency goes in its own column.")
                    .aliases("amount", "cost", "price", "total value", "annual value").build(),
            field("currency", "Currency", ImportDataType.STRING)
                    .example("GHS")
                    .notes("Three-letter ISO 4217 code, e.g. GHS, USD, GBP.")
                    .aliases("ccy", "currency code", "iso currency").build(),
            field("autoRenew", "Auto renew", ImportDataType.BOOLEAN)
                    .example("no")
                    .notes("yes / no, true / false, 1 / 0.")
                    .aliases("auto renewal", "automatic renewal", "auto-renew", "evergreen").build(),
            field("documentUrl", "Document URL", ImportDataType.STRING)
                    .example("")
                    .notes("An http or https link to the signed agreement.")
                    .aliases("document link", "contract url", "attachment url", "file url").build(),
            field("notes", "Notes", ImportDataType.TEXT)
                    .example("")
                    .aliases("comments", "remarks", "details").build()
    );

    private final ContractService contractService;
    private final ContractRepository contractRepository;
    private final ImportReferenceResolver referenceResolver;
    private final ImportBeanValidator beanValidator;

    public ContractImportHandler(ContractService contractService,
                                 ContractRepository contractRepository,
                                 ImportReferenceResolver referenceResolver,
                                 ImportBeanValidator beanValidator) {
        this.contractService = contractService;
        this.contractRepository = contractRepository;
        this.referenceResolver = referenceResolver;
        this.beanValidator = beanValidator;
    }

    @Override
    public ImportEntityType entityType() {
        return ImportEntityType.CONTRACTS;
    }

    @Override
    public List<ImportFieldDescriptor> fields() {
        return FIELDS;
    }

    @Override
    public ImportRunner runner(Organisation organisation, ImportOptions options) {
        return new Runner(organisation, options);
    }

    private final class Runner extends AbstractImportRunner<ContractDto> {

        private final ImportReferenceResolver.Refs refs;
        private final Map<String, UUID> byNumber = new LinkedHashMap<>();
        private final Map<String, UUID> byTitle = new LinkedHashMap<>();

        private Runner(Organisation organisation, ImportOptions options) {
            super(options);
            this.refs = referenceResolver.open(organisation, options);
            for (Contract contract : contractRepository.findByOrganisationAndDeletedAtIsNullOrderByEndDateAsc(organisation)) {
                if (contract.getContractNumber() != null && !contract.getContractNumber().isBlank()) {
                    byNumber.putIfAbsent(key(contract.getContractNumber()), contract.getId());
                }
                if (contract.getTitle() != null) {
                    byTitle.putIfAbsent(key(contract.getTitle()), contract.getId());
                }
            }
        }

        @Override
        protected ContractDto build(ImportRow row) {
            ContractDto dto = new ContractDto();
            dto.setTitle(row.requiredString("title"));
            dto.setContractNumber(row.string("contractNumber"));
            dto.setContractType(row.enumValue(ContractType.class, "contractType"));
            if (dto.getContractType() == null) {
                throw new FieldValidationException("contractType", "is required but was blank");
            }
            dto.setStatus(row.enumValue(ContractStatus.class, "status"));
            dto.setSupplierId(refs.supplier(row.string("supplier"), "supplier"));
            dto.setAssetId(refs.asset(row.string("asset"), "asset"));
            dto.setStartDate(row.date("startDate"));
            dto.setEndDate(row.date("endDate"));
            if (dto.getStartDate() == null) {
                throw new FieldValidationException("startDate", "is required but was blank");
            }
            if (dto.getEndDate() == null) {
                throw new FieldValidationException("endDate", "is required but was blank");
            }
            if (dto.getEndDate().isBefore(dto.getStartDate())) {
                throw new FieldValidationException("endDate",
                        "is before the start date (" + dto.getStartDate() + ")");
            }
            dto.setAlertDaysBefore(row.integer("alertDaysBefore"));
            dto.setValue(row.decimal("value"));
            String currency = row.string("currency");
            dto.setCurrency(currency == null ? null : currency.toUpperCase(Locale.ROOT));
            dto.setAutoRenew(row.bool("autoRenew"));
            dto.setDocumentUrl(row.string("documentUrl"));
            dto.setNotes(row.string("notes"));
            beanValidator.validateForCreate(dto);
            return dto;
        }

        @Override
        protected UUID findExisting(ImportRow row, ContractDto payload) {
            if (payload.getContractNumber() != null && !payload.getContractNumber().isBlank()) {
                return byNumber.get(key(payload.getContractNumber()));
            }
            return byTitle.get(key(payload.getTitle()));
        }

        @Override
        protected void create(ContractDto payload) {
            ContractDto created = contractService.create(payload);
            if (payload.getContractNumber() != null && !payload.getContractNumber().isBlank()) {
                byNumber.put(key(payload.getContractNumber()), created.getId());
            }
            byTitle.putIfAbsent(key(payload.getTitle()), created.getId());
        }

        @Override
        protected void update(UUID id, ContractDto payload) {
            contractService.patch(id, payload);
        }

        @Override
        protected String describe() {
            return "contract";
        }

        @Override
        protected String naturalKey(ImportRow row, ContractDto payload) {
            return payload.getContractNumber() != null && !payload.getContractNumber().isBlank()
                    ? "contract number '" + payload.getContractNumber() + "'"
                    : "title '" + payload.getTitle() + "'";
        }

        private String key(String value) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
