package pl.example.importer.application.port.in;

import pl.example.importer.domain.RecordType;
import java.util.Map;

public record ImportDefinition(String name, String pathTemplate, String schemaEntry, String csvEntry,
                               char delimiter, int batchSize, RecordType recordType,
                               Map<String, String> headers) {
    public ImportDefinition {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (pathTemplate == null || pathTemplate.isBlank()) throw new IllegalArgumentException("path must not be blank");
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
