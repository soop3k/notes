package pl.example.importer.adapter.out.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.example.importer.domain.ImportedRow;

import java.time.Clock;
import java.util.UUID;

public final class ImportedRowEntityMapper {
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ImportedRowEntityMapper(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    ImportedRowEntity toEntity(UUID executionId, String source, ImportedRow row) {
        try {
            return new ImportedRowEntity(executionId, source, row.number(), row.data().type().name(), row.data().businessKey(),
                    objectMapper.writeValueAsString(row.data()), clock.instant());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize row " + row.number(), exception);
        }
    }
}
