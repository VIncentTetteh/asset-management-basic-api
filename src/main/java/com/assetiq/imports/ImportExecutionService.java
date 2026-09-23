package com.assetiq.imports;

import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.models.Organisation;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Reads a staged file and runs it through the engine for a given entity type.
 *
 * <p>The one place preview and commit meet: a preview is this with a row cap and
 * {@code dryRun}, a commit is this without either. They cannot disagree about what a
 * row means, because they are the same call.</p>
 */
@Service
public class ImportExecutionService {

    private final SpreadsheetReader spreadsheetReader;
    private final ImportDescriptorRegistry registry;
    private final ImportEngine engine;

    public ImportExecutionService(SpreadsheetReader spreadsheetReader,
                                  ImportDescriptorRegistry registry,
                                  ImportEngine engine) {
        this.spreadsheetReader = spreadsheetReader;
        this.registry = registry;
        this.engine = engine;
    }

    public AssetImportResultDto execute(ImportEntityType type,
                                        Organisation organisation,
                                        String filename,
                                        byte[] bytes,
                                        Map<String, Integer> mapping,
                                        ImportOptions options,
                                        int rowLimit) {
        ParsedSheet sheet = spreadsheetReader.read(filename, bytes);
        return engine.run(sheet, mapping, registry.handler(type), options, organisation, rowLimit);
    }
}
