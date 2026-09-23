package com.assetiq.imports.handlers;

import com.assetiq.dto.LocationDto;
import com.assetiq.imports.AbstractImportRunner;
import com.assetiq.imports.FieldValidationException;
import com.assetiq.imports.ImportBeanValidator;
import com.assetiq.imports.ImportDataType;
import com.assetiq.imports.ImportEntityHandler;
import com.assetiq.imports.ImportEntityType;
import com.assetiq.imports.ImportFieldDescriptor;
import com.assetiq.imports.ImportOptions;
import com.assetiq.imports.ImportRow;
import com.assetiq.models.Location;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.LocationRepository;
import com.assetiq.services.LocationService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.assetiq.imports.ImportFieldDescriptor.field;

/** Locations. Duplicates are decided on location name, case-insensitively. */
@Component
public class LocationImportHandler implements ImportEntityHandler {

    private static final List<ImportFieldDescriptor> FIELDS = List.of(
            field("name", "Location name", ImportDataType.STRING).required()
                    .example("Accra Head Office")
                    .notes("Must be unique within your organisation.")
                    .aliases("site", "site name", "location", "location name", "location title",
                            "name", "office", "branch", "facility", "premises",
                            "depot", "warehouse").build(),
            field("building", "Building", ImportDataType.STRING)
                    .example("Tower A")
                    .aliases("block", "building name", "building/block", "tower").build(),
            field("floor", "Floor", ImportDataType.STRING)
                    .example("3rd")
                    .aliases("level", "floor number", "floor no", "level number", "storey").build(),
            field("room", "Room", ImportDataType.STRING)
                    .example("3.12")
                    .aliases("office number", "room number", "room name", "room no", "room no.",
                            "room #", "desk", "space", "suite").build(),
            field("city", "City", ImportDataType.STRING)
                    .example("Accra")
                    .aliases("town", "city/town", "locality").build(),
            field("country", "Country code", ImportDataType.STRING)
                    .example("GH")
                    .notes("Two-letter ISO 3166-1 code in capitals, e.g. GH, GB, US.")
                    .aliases("country", "iso country", "country code").build(),
            field("address", "Address", ImportDataType.TEXT)
                    .example("12 Independence Ave, Accra")
                    .aliases("street address", "address line 1", "full address", "postal address",
                            "street").build(),
            field("latitude", "Latitude", ImportDataType.DECIMAL)
                    .example("5.6037")
                    .notes("Decimal degrees between -90 and 90.")
                    .aliases("lat", "gps latitude", "geo lat").build(),
            field("longitude", "Longitude", ImportDataType.DECIMAL)
                    .example("-0.1870")
                    .notes("Decimal degrees between -180 and 180.")
                    .aliases("lng", "lon", "gps longitude", "geo lng").build(),
            field("parentLocation", "Parent location", ImportDataType.REFERENCE)
                    .example("")
                    .notes("Name of another location. It may appear in an earlier row of this file.")
                    .aliases("parent", "parent site", "parent site name", "parent location name",
                            "belongs to", "within").build()
    );

    private final LocationService locationService;
    private final LocationRepository locationRepository;
    private final ImportBeanValidator beanValidator;

    public LocationImportHandler(LocationService locationService,
                                 LocationRepository locationRepository,
                                 ImportBeanValidator beanValidator) {
        this.locationService = locationService;
        this.locationRepository = locationRepository;
        this.beanValidator = beanValidator;
    }

    @Override
    public ImportEntityType entityType() {
        return ImportEntityType.LOCATIONS;
    }

    @Override
    public List<ImportFieldDescriptor> fields() {
        return FIELDS;
    }

    @Override
    public ImportRunner runner(Organisation organisation, ImportOptions options) {
        return new Runner(organisation, options);
    }

    private final class Runner extends AbstractImportRunner<LocationDto> {

        private final Organisation organisation;
        private final Map<String, UUID> byName = new LinkedHashMap<>();

        private Runner(Organisation organisation, ImportOptions options) {
            super(options);
            this.organisation = organisation;
            for (Location location : locationRepository.findByOrganisationAndDeletedAtIsNull(organisation)) {
                if (location.getName() != null) {
                    byName.putIfAbsent(key(location.getName()), location.getId());
                }
            }
        }

        @Override
        protected LocationDto build(ImportRow row) {
            LocationDto dto = new LocationDto();
            dto.setName(row.requiredString("name"));
            dto.setBuilding(row.string("building"));
            dto.setFloor(row.string("floor"));
            dto.setRoom(row.string("room"));
            dto.setCity(row.string("city"));
            String country = row.string("country");
            dto.setCountry(country == null ? null : country.toUpperCase(Locale.ROOT));
            dto.setAddress(row.string("address"));
            dto.setLatitude(toDouble(row, "latitude", -90, 90));
            dto.setLongitude(toDouble(row, "longitude", -180, 180));

            String parent = row.string("parentLocation");
            if (parent != null) {
                UUID parentId = byName.get(key(parent));
                if (parentId == null) {
                    throw new FieldValidationException("parentLocation",
                            "names '" + parent + "', which is not a location in your organisation."
                                    + " Put the parent in an earlier row, or create it first.");
                }
                dto.setParentLocationId(parentId);
            }
            beanValidator.validateForCreate(dto);
            return dto;
        }

        private Double toDouble(ImportRow row, String field, double min, double max) {
            BigDecimal value = row.decimal(field);
            if (value == null) return null;
            double result = value.doubleValue();
            if (result < min || result > max) {
                throw new FieldValidationException(field,
                        "must be between " + min + " and " + max + " but was " + value.toPlainString());
            }
            return result;
        }

        @Override
        protected UUID findExisting(ImportRow row, LocationDto payload) {
            return byName.get(key(payload.getName()));
        }

        @Override
        protected void create(LocationDto payload) {
            LocationDto created = locationService.createLocation(payload, organisation.getId());
            byName.put(key(payload.getName()), created.getId());
        }

        @Override
        protected void update(UUID id, LocationDto payload) {
            locationService.patchLocation(id, payload);
        }

        @Override
        protected String describe() {
            return "location";
        }

        @Override
        protected String naturalKey(ImportRow row, LocationDto payload) {
            return "location name '" + payload.getName() + "'";
        }

        private String key(String value) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
