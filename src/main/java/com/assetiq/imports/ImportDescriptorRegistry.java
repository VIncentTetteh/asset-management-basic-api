package com.assetiq.imports;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The one place that answers "what can be imported, and what are its fields?".
 *
 * <p>Handlers register themselves by being Spring beans, so adding an entity type
 * never means editing a list here.</p>
 */
@Component
public class ImportDescriptorRegistry {

    private final Map<ImportEntityType, ImportEntityHandler> handlers = new EnumMap<>(ImportEntityType.class);

    public ImportDescriptorRegistry(List<ImportEntityHandler> discovered) {
        for (ImportEntityHandler handler : discovered) {
            ImportEntityHandler previous = handlers.put(handler.entityType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Two import handlers claim " + handler.entityType()
                        + ": " + previous.getClass().getName() + " and " + handler.getClass().getName());
            }
        }
    }

    /** Entity types that actually have a handler, in declaration order. */
    public List<ImportEntityType> supportedTypes() {
        return java.util.Arrays.stream(ImportEntityType.values())
                .filter(handlers::containsKey)
                .toList();
    }

    public ImportEntityHandler handler(ImportEntityType type) {
        ImportEntityHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("Importing " + type.label().toLowerCase(java.util.Locale.ROOT)
                    + " is not supported");
        }
        return handler;
    }

    public List<ImportFieldDescriptor> fields(ImportEntityType type) {
        return handler(type).fields();
    }
}
