package pl.example.importer.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.example.importer.ImporterProperties;
import pl.example.importer.source.ArchiveSource;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Application use case coordinating a source, archive traversal and CSV processing. */
public final class ZipImportService {
    private static final Logger log = LoggerFactory.getLogger(ZipImportService.class);
    private final ImporterProperties properties;
    private final ArchiveSource source;
    private final CsvImportProcessor csvProcessor;
    private final ObjectMapper mapper;
    private final PathTemplateResolver pathResolver;

    public ZipImportService(ImporterProperties properties, ArchiveSource source,
                            CsvImportProcessor csvProcessor, ObjectMapper mapper,
                            PathTemplateResolver pathResolver) {
        this.properties = properties;
        this.source = source;
        this.csvProcessor = csvProcessor;
        this.mapper = mapper;
        this.pathResolver = pathResolver;
    }

    public long importArchive() throws IOException {
        String path = pathResolver.resolve(properties.path());
        log.info("Starting streaming import from {} ({})", path, properties.mode());
        try (InputStream input = new BufferedInputStream(source.open(path));
             ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            JsonSchema schema = null;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entry.getName().equals(properties.schemaEntry())) {
                    JsonNode schemaNode = mapper.readTree(zip);
                    schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
                } else if (entry.getName().equals(properties.csvEntry())) {
                    if (schema == null) throw new IOException("Schema entry must occur before CSV entry in a streaming ZIP");
                    long rows = csvProcessor.process(zip, schema, path);
                    log.info("Imported {} rows from {}", rows, path);
                    return rows;
                }
                zip.closeEntry();
            }
        }
        throw new IOException("Archive does not contain configured schema and CSV entries");
    }
}
