package pl.example.importer.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.networknt.schema.JsonSchema;
import pl.example.importer.application.port.out.ImportedRowSink;
import pl.example.importer.application.port.out.CsvRecordMapper;
import pl.example.importer.application.port.in.ImportDefinition;
import pl.example.importer.domain.ImportedRow;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CsvRowImporter {
    private final CsvMapper csvMapper;
    private final ObjectMapper objectMapper;
    private final CsvRecordMapper recordMapper;
    private final ImportedRowSink rowSink;

    public CsvRowImporter(CsvMapper csvMapper, ObjectMapper objectMapper,
                          CsvRecordMapper recordMapper, ImportedRowSink rowSink) {
        this.csvMapper = csvMapper;
        this.objectMapper = objectMapper;
        this.recordMapper = recordMapper;
        this.rowSink = rowSink;
    }

    long importRows(UUID executionId, String source, InputStream input, JsonSchema jsonSchema,
                    ImportDefinition definition) throws IOException {
        CsvSchema csvSchema = CsvSchema.emptySchema().withHeader().withColumnSeparator(definition.delimiter());
        List<ImportedRow> batch = new ArrayList<>(definition.batchSize());
        long rowNumber = 0;
        try (MappingIterator<Map<String, String>> records = csvMapper
                .readerFor(new TypeReference<Map<String, String>>() { })
                .with(csvSchema).readValues(input)) {
            while (records.hasNextValue()) {
                Map<String, String> rawRow = records.nextValue();
                var data = recordMapper.map(definition.recordType(), definition.headers(), rawRow);
                rowNumber++;
                var validationErrors = jsonSchema.validate(objectMapper.valueToTree(data));
                if (!validationErrors.isEmpty()) throw new InvalidRowException(rowNumber, validationErrors);
                batch.add(new ImportedRow(rowNumber, data));
                if (batch.size() == definition.batchSize()) flush(executionId, source, batch);
            }
        }
        flush(executionId, source, batch);
        return rowNumber;
    }

    private void flush(UUID executionId, String source, List<ImportedRow> batch) {
        if (batch.isEmpty()) return;
        rowSink.save(executionId, source, List.copyOf(batch));
        batch.clear();
    }
}
