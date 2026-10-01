package pl.example.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {"importer.path=unused", "importer.batch-size=2",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"})
class ZipImportServiceTest {
    @TempDir Path directory;
    @Autowired ImporterProperties properties;
    @Autowired ArchiveSource source;
    @Autowired RowRepository repository;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test void streamsSchemaAndCsvIntoDatabase() throws Exception {
        Path archive = directory.resolve("input.zip");
        try (OutputStream output = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(output)) {
            add(zip, "schema.json", """
                    {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
                     "required":["id","name"],"properties":{"id":{"type":"string"},"name":{"type":"string"}}}
                    """);
            add(zip, "data.csv", "id,name\n1,Ala\n2,Ola\n3,Jan\n");
        }
        ImporterProperties testProperties = new ImporterProperties(ImporterProperties.Mode.LOCAL,
                archive.toString(), "schema.json", "data.csv", ',', 2, false,
                java.time.ZoneId.of("UTC"), properties.sftp());
        ZipImportService service = new ZipImportService(testProperties, source, repository, mapper);

        assertThat(service.importArchive()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from imported_row", Long.class)).isEqualTo(3);
    }

    private static void add(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
