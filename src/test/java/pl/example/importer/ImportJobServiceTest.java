package pl.example.importer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import pl.example.importer.application.port.in.ImportDefinition;
import pl.example.importer.application.port.in.PollImportJobsUseCase;
import pl.example.importer.application.port.in.ScheduleImportJobUseCase;
import pl.example.importer.domain.RecordType;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {"importer.polling.fixed-delay=1h", "importer.polling.file-check-interval=1ms",
        "spring.datasource.url=jdbc:h2:mem:jobtest;DB_CLOSE_DELAY=-1"})
class ImportJobServiceTest {
    @Autowired ScheduleImportJobUseCase scheduler;
    @Autowired PollImportJobsUseCase poller;
    @Autowired JdbcTemplate jdbc;

    @Test
    void waitsForARequestSpecificFileThenImportsUsingItsHeaderMapping() throws Exception {
        Path archive = Path.of("target", "job-" + UUID.randomUUID() + ".zip");
        Files.deleteIfExists(archive);
        var definition = new ImportDefinition("request-customers", archive.toString(), "schema.json", "data.csv",
                ';', 2, RecordType.CUSTOMER, Map.of("customerId", "client-code", "fullName", "display-name"));

        UUID jobId = scheduler.schedule(new ScheduleImportJobUseCase.ScheduleImportJobCommand(
                "request-" + UUID.randomUUID(), definition));
        poller.poll();
        assertThat(status(jobId)).isEqualTo("WAITING_FOR_FILE");

        Files.createDirectories(archive.getParent());
        try (OutputStream output = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(output)) {
            add(zip, "schema.json", """
                    {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
                     "required":["customerId","fullName"],
                     "properties":{"customerId":{"type":"string"},"fullName":{"type":"string"}}}
                    """);
            add(zip, "data.csv", "client-code;display-name\nC-1;Jan Kowalski\n");
        }
        Thread.sleep(5);
        poller.poll();

        assertThat(status(jobId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from imported_row", Long.class)).isEqualTo(1);
    }

    private String status(UUID id) {
        return jdbc.queryForObject("select status from import_job where id=?", String.class, id);
    }

    private static void add(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
