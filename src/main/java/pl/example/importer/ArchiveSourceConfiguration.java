package pl.example.importer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ArchiveSourceConfiguration {
    @Bean ArchiveSource archiveSource(ImporterProperties properties) {
        return properties.mode() == ImporterProperties.Mode.SFTP
                ? new SftpArchiveSource(properties.sftp()) : new LocalArchiveSource();
    }
}
