package com.assetiq.imports;

import com.assetiq.imports.handlers.AssetImportHandler;
import com.assetiq.imports.handlers.CategoryImportHandler;
import com.assetiq.imports.handlers.ContractImportHandler;
import com.assetiq.imports.handlers.DepartmentImportHandler;
import com.assetiq.imports.handlers.EmployeeImportHandler;
import com.assetiq.imports.handlers.LocationImportHandler;
import com.assetiq.imports.handlers.SoftwareLicenceImportHandler;
import com.assetiq.imports.handlers.SupplierImportHandler;

import java.util.List;

/**
 * A handler's descriptors without standing up Spring.
 *
 * <p>Every handler declares its fields as a constant and takes no part in building
 * them, so a handler constructed with null dependencies answers {@code fields()}
 * perfectly well. That keeps the descriptor tests fast and free of mocks.</p>
 */
final class ImportTestDescriptors {

    private ImportTestDescriptors() {}

    static List<ImportFieldDescriptor> fieldsFor(ImportEntityType type) {
        return switch (type) {
            case ASSETS -> new AssetImportHandler(null, null, null, null, null, null, null, null).fields();
            case SUPPLIERS -> new SupplierImportHandler(null, null, null).fields();
            case EMPLOYEES -> new EmployeeImportHandler(null, null, null, null).fields();
            case LOCATIONS -> new LocationImportHandler(null, null, null).fields();
            case DEPARTMENTS -> new DepartmentImportHandler(null, null, null, null).fields();
            case CATEGORIES -> new CategoryImportHandler(null, null, null).fields();
            case SOFTWARE_LICENCES -> new SoftwareLicenceImportHandler(null, null, null, null).fields();
            case CONTRACTS -> new ContractImportHandler(null, null, null, null).fields();
        };
    }
}
