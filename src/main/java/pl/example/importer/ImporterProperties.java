package pl.example.importer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import pl.example.importer.domain.RecordType;

@Validated
@ConfigurationProperties("importer")
public record ImporterProperties(
        Mode mode,
        boolean runOnStartup,
        ZoneId zone,
        @NotEmpty Map<@NotBlank String, @Valid Job> jobs,
        @Valid Sftp sftp
) {
    public ImporterProperties {
        mode = mode == null ? Mode.LOCAL : mode;
        zone = zone == null ? ZoneId.of("UTC") : zone;
        jobs = jobs == null ? Map.of() : Map.copyOf(jobs);
    }

    public enum Mode { LOCAL, SFTP }
    public record Job(
            boolean enabled,
            @NotBlank String path,
            String schemaEntry,
            String csvEntry,
            Character delimiter,
            @Min(1) Integer batchSize,
            @NotNull RecordType recordType,
            Map<String, String> headers
    ) {
        public Job {
            schemaEntry = schemaEntry == null || schemaEntry.isBlank() ? "schema.json" : schemaEntry;
            csvEntry = csvEntry == null || csvEntry.isBlank() ? "data.csv" : csvEntry;
            delimiter = delimiter == null ? ',' : delimiter;
            batchSize = batchSize == null ? 500 : batchSize;
            headers = headers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(headers));
        }
    }

    public record Sftp(String host, int port, String username, String password,
                       Path privateKey, String privateKeyPassphrase,
                       Path knownHosts, boolean allowUnknownHost) {
        public Sftp {
            port = port == 0 ? 22 : port;
        }
    }
}
