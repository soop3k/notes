package pl.example.importer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.example.importer.application.port.out.CsvRecordMapper;
import pl.example.importer.domain.ImportRecord;
import pl.example.importer.domain.RecordType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JacksonCsvRecordMapper implements CsvRecordMapper {
    private final ObjectMapper objectMapper;
    private final Map<RecordType, RecordMappingDefinition> definitions;

    public JacksonCsvRecordMapper(ObjectMapper objectMapper, List<RecordMappingDefinition> definitions) {
        this.objectMapper = objectMapper;
        this.definitions = definitions.stream().collect(Collectors.toUnmodifiableMap(
                RecordMappingDefinition::type, Function.identity()));
    }

    @Override
    public ImportRecord map(RecordType type, Map<String, String> headers, Map<String, String> row) {
        RecordMappingDefinition definition = definitions.get(type);
        if (definition == null) throw new IllegalArgumentException("Unsupported record type: " + type);
        validateConfiguration(definition, headers);
        Map<String, String> normalized = new LinkedHashMap<>();
        definition.fields().forEach(field -> {
            String sourceHeader = headers.getOrDefault(field, field);
            if (!row.containsKey(sourceHeader)) {
                throw new IllegalArgumentException("CSV does not contain configured header '" + sourceHeader + "'");
            }
            normalized.put(field, row.get(sourceHeader));
        });
        return objectMapper.convertValue(normalized, definition.targetClass());
    }

    private void validateConfiguration(RecordMappingDefinition definition, Map<String, String> headers) {
        if (!definition.fields().containsAll(headers.keySet())) {
            throw new IllegalArgumentException("Unknown target fields for " + definition.type() + ": " + headers.keySet());
        }
        if (new HashSet<>(headers.values()).size() != headers.values().size()) {
            throw new IllegalArgumentException("One source header cannot map to multiple target fields");
        }
    }
}
