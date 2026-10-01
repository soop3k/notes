package pl.example.importer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import pl.example.importer.domain.ImportedRow;
import pl.example.importer.persistence.ImportedRowWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns CSV decoding, row mapping, validation and bounded batch delivery. */
public final class CsvImportProcessor {
    private final ObjectMapper mapper;
    private final ImportedRowWriter writer;
    private final char delimiter;
    private final int batchSize;

    public CsvImportProcessor(ObjectMapper mapper, ImportedRowWriter writer, char delimiter, int batchSize) {
        this.mapper = mapper;
        this.writer = writer;
        this.delimiter = delimiter;
        this.batchSize = batchSize;
    }

    public long process(InputStream input, JsonSchema schema, String source) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter)
                .setHeader().setSkipHeaderRecord(true).get();
        List<ImportedRow> batch = new ArrayList<>(batchSize);
        long count = 0;
        try (CSVParser parser = format.parse(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (CSVRecord record : parser) {
                Map<String, String> values = new LinkedHashMap<>();
                parser.getHeaderMap().keySet().forEach(header -> values.put(header, record.get(header)));
                var node = mapper.valueToTree(values);
                var errors = schema.validate(node);
                if (!errors.isEmpty()) {
                    throw new IOException("Invalid CSV row " + record.getRecordNumber() + ": " + errors);
                }
                batch.add(new ImportedRow(record.getRecordNumber(), mapper.writeValueAsString(node)));
                count++;
                if (batch.size() == batchSize) flush(source, batch);
            }
        }
        flush(source, batch);
        return count;
    }

    private void flush(String source, List<ImportedRow> batch) {
        if (batch.isEmpty()) return;
        writer.saveBatch(source, List.copyOf(batch));
        batch.clear();
    }
}
