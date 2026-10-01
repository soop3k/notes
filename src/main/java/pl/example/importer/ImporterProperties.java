package pl.example.importer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.ZoneId;

@Validated
@ConfigurationProperties("importer")
public record ImporterProperties(
        Mode mode,
        @NotBlank String path,
        String schemaEntry,
        String csvEntry,
        char delimiter,
        @Min(1) int batchSize,
        boolean runOnStartup,
        ZoneId zone,
        @Valid Sftp sftp
) {
    public ImporterProperties {
        mode = mode == null ? Mode.LOCAL : mode;
        schemaEntry = schemaEntry == null ? "schema.json" : schemaEntry;
        csvEntry = csvEntry == null ? "data.csv" : csvEntry;
        delimiter = delimiter == '\0' ? ',' : delimiter;
        batchSize = batchSize == 0 ? 500 : batchSize;
        zone = zone == null ? ZoneId.of("UTC") : zone;
    }

    public enum Mode { LOCAL, SFTP }

    public record Sftp(String host, int port, String username, String password,
                       Path privateKey, String privateKeyPassphrase,
                       Path knownHosts, boolean allowUnknownHost) {
        public Sftp {
            port = port == 0 ? 22 : port;
            allowUnknownHost = allowUnknownHost;
        }
    }
}
