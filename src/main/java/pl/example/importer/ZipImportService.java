package pl.example.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ZipImportService {
    private static final Logger log = LoggerFactory.getLogger(ZipImportService.class);
    private final ImporterProperties properties;
    private final ArchiveSource source;
    private final RowRepository repository;
    private final ObjectMapper mapper;

    public ZipImportService(ImporterProperties properties, ArchiveSource source,
                            RowRepository repository, ObjectMapper mapper) {
        this.properties = properties;
        this.source = source;
        this.repository = repository;
        this.mapper = mapper;
    }

    public long importArchive() throws IOException {
        String path = resolvePath(properties.path());
        log.info("Starting streaming import from {} ({})", path, properties.mode());
        try (InputStream input = new BufferedInputStream(source.open(path));
             ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            JsonSchema schema = null;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entry.getName().equals(properties.schemaEntry())) {
                    JsonNode schemaNode = mapper.readTree(zip);
                    schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                            .getSchema(schemaNode);
                } else if (entry.getName().equals(properties.csvEntry())) {
                    if (schema == null) {
                        throw new IOException("Schema entry must occur before CSV entry in a streaming ZIP");
                    }
                    long rows = importCsv(zip, schema, path);
                    log.info("Imported {} rows from {}", rows, path);
                    return rows;
                }
                zip.closeEntry();
            }
        }
        throw new IOException("Archive does not contain configured schema and CSV entries");
    }

    private long importCsv(InputStream input, JsonSchema schema, String path) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(properties.delimiter())
                .setHeader().setSkipHeaderRecord(true).get();
        List<RowRepository.Row> batch = new ArrayList<>(properties.batchSize());
        long count = 0;
        try (CSVParser parser = format.parse(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (CSVRecord record : parser) {
                Map<String, String> values = new LinkedHashMap<>();
                parser.getHeaderMap().keySet().forEach(header -> values.put(header, record.get(header)));
                String json = mapper.writeValueAsString(values);
                var errors = schema.validate(mapper.readTree(json));
                if (!errors.isEmpty()) {
                    throw new IOException("Invalid CSV row " + record.getRecordNumber() + ": " + errors);
                }
                batch.add(new RowRepository.Row(record.getRecordNumber(), json));
                count++;
                if (batch.size() == properties.batchSize()) flush(path, batch);
            }
        }
        flush(path, batch);
        return count;
    }

    private void flush(String path, List<RowRepository.Row> batch) {
        if (batch.isEmpty()) return;
        repository.saveBatch(path, batch);
        batch.clear();
    }

    String resolvePath(String pattern) {
        ZonedDateTime now = ZonedDateTime.now(properties.zone());
        return pattern.replace("{date}", now.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .replace("{datetime}", now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
    }
}
