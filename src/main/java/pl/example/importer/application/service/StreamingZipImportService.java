package pl.example.importer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.example.importer.application.port.in.ImportArchiveUseCase;
import pl.example.importer.application.port.in.ImportDefinition;
import pl.example.importer.application.port.out.ArchiveSource;
import pl.example.importer.application.port.out.ImportExecutionStore;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class StreamingZipImportService implements ImportArchiveUseCase {
    private static final Logger log = LoggerFactory.getLogger(StreamingZipImportService.class);
    private final ArchiveSource archiveSource;
    private final ImportExecutionStore executions;
    private final CsvRowImporter csvImporter;
    private final ObjectMapper objectMapper;
    private final PathTemplateResolver pathResolver;
    private final Clock clock;

    public StreamingZipImportService(ArchiveSource archiveSource, ImportExecutionStore executions,
            CsvRowImporter csvImporter, ObjectMapper objectMapper, PathTemplateResolver pathResolver,
            Clock clock) {
        this.archiveSource = archiveSource;
        this.executions = executions;
        this.csvImporter = csvImporter;
        this.objectMapper = objectMapper;
        this.pathResolver = pathResolver;
        this.clock = clock;
    }

    @Override
    public ImportResult importArchive(ImportCommand command) {
        var definition = command.definition();
        String source = pathResolver.resolve(definition.pathTemplate());
        UUID executionId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        executions.started(executionId, definition.name(), source, startedAt);
        log.info("Import {} started for {}", executionId, source);
        try {
            long rows = streamArchive(executionId, source, definition);
            Instant completedAt = clock.instant();
            executions.completed(executionId, rows, completedAt);
            log.info("Import {} completed: {} rows", executionId, rows);
            return new ImportResult(executionId, definition.name(), source, rows, startedAt, completedAt);
        } catch (Exception failure) {
            try {
                executions.failed(executionId, failure.getMessage(), clock.instant());
            } catch (RuntimeException trackingFailure) {
                failure.addSuppressed(trackingFailure);
            }
            throw new ImportException(source, "Import " + executionId + " failed for " + source, failure);
        }
    }

    private long streamArchive(UUID executionId, String source, ImportDefinition definition) throws IOException {
        try (var input = new BufferedInputStream(archiveSource.open(source));
             var zip = new ZipInputStream(input)) {
            JsonSchema schema = null;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entry.getName().equals(definition.schemaEntry())) {
                    schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                            .getSchema(objectMapper.readTree(zip));
                } else if (entry.getName().equals(definition.csvEntry())) {
                    if (schema == null) throw new IOException("Schema entry must precede CSV entry");
                    return csvImporter.importRows(executionId, source, zip, schema, definition);
                }
                zip.closeEntry();
            }
        }
        throw new IOException("Configured schema and CSV entries were not found in archive");
    }
}
