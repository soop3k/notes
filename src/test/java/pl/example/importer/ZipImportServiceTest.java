package pl.example.importer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import pl.example.importer.application.port.in.ImportArchiveUseCase;
import pl.example.importer.application.port.in.ImportDefinition;
import pl.example.importer.application.service.InvalidRowException;
import pl.example.importer.domain.RecordType;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {"importer.jobs.test.path=target/test-import.zip",
        "importer.jobs.test.record-type=customer", "importer.jobs.test.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"})
class ZipImportServiceTest {
    @Autowired ImportArchiveUseCase useCase;
    @Autowired JdbcTemplate jdbc;

    @Test void streamsSchemaAndCsvIntoDatabase() throws Exception {
        Path archive = Path.of("target/test-import.zip");
        Files.createDirectories(archive.getParent());
        try (OutputStream output = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(output)) {
            add(zip, "schema.json", """
                    {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
                     "required":["customerId","fullName"],
                     "properties":{"customerId":{"type":"string"},"fullName":{"type":"string"}}}
                    """);
            add(zip, "data.csv", "client_no,display_name\n1,Ala\n2,Ola\n3,Jan\n");
        }
        var definition = new ImportDefinition("customers", archive.toString(), "schema.json", "data.csv",
                ',', 2, RecordType.CUSTOMER,
                java.util.Map.of("customerId", "client_no", "fullName", "display_name"));
        var result = useCase.importArchive(new ImportArchiveUseCase.ImportCommand(definition));
        assertThat(result.importedRows()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from imported_row", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select payload from imported_row where row_number = 1", String.class))
                .isEqualTo("{\"customerId\":\"1\",\"fullName\":\"Ala\"}");
        assertThat(jdbc.queryForObject("select status from import_execution where id = ?", String.class,
                result.executionId())).isEqualTo("COMPLETED");
    }

    @Test void recordsFailedExecutionWhenARecordIsInvalid() throws Exception {
        Path archive = Path.of("target/invalid-import.zip");
        Files.createDirectories(archive.getParent());
        try (OutputStream output = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(output)) {
            add(zip, "schema.json", """
                    {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
                     "required":["customerId","fullName"],
                     "properties":{"customerId":{"type":"string"},"fullName":{"type":"string","minLength":1}}}
                    """);
            add(zip, "data.csv", "customerId,fullName\n1,\n");
        }

        var definition = new ImportDefinition("invalid-customers", archive.toString(), "schema.json", "data.csv",
                ',', 2, RecordType.CUSTOMER, java.util.Map.of());
        assertThatThrownBy(() -> useCase.importArchive(new ImportArchiveUseCase.ImportCommand(definition)))
                .hasMessageContaining("failed for").hasRootCauseInstanceOf(InvalidRowException.class);
        assertThat(jdbc.queryForObject("select status from import_execution where source_file = ?", String.class,
                archive.toString())).isEqualTo("FAILED");
    }

    private static void add(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
