package pl.example.importer;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.Duration;

@Validated
@ConfigurationProperties("importer")
public record ImporterProperties(
        Mode mode,
        ZoneId zone,
        @Valid Polling polling,
        @Valid Sftp sftp
) {
    public ImporterProperties {
        mode = mode == null ? Mode.LOCAL : mode;
        zone = zone == null ? ZoneId.of("UTC") : zone;
        polling = polling == null ? new Polling(null, null, null) : polling;
    }

    public enum Mode { LOCAL, SFTP }
    public record Polling(Duration fixedDelay, Duration fileCheckInterval, Integer batchSize) {
        public Polling {
            fixedDelay = fixedDelay == null ? Duration.ofSeconds(5) : fixedDelay;
            fileCheckInterval = fileCheckInterval == null ? Duration.ofSeconds(30) : fileCheckInterval;
            batchSize = batchSize == null ? 10 : batchSize;
            if (fixedDelay.isNegative() || fixedDelay.isZero()) throw new IllegalArgumentException("fixedDelay must be positive");
            if (fileCheckInterval.isNegative() || fileCheckInterval.isZero()) throw new IllegalArgumentException("fileCheckInterval must be positive");
            if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
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
