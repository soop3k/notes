package pl.example.importer.config;

import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.util.StringUtils;
import pl.example.importer.ImporterProperties;
import pl.example.importer.source.ArchiveSource;
import pl.example.importer.source.LocalArchiveSource;
import pl.example.importer.source.SftpArchiveSource;

@Configuration(proxyBeanMethods = false)
public class ArchiveSourceConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "importer", name = "mode", havingValue = "local", matchIfMissing = true)
    ArchiveSource localArchiveSource() {
        return new LocalArchiveSource();
    }

    @Bean
    @ConditionalOnProperty(prefix = "importer", name = "mode", havingValue = "sftp")
    ArchiveSource sftpArchiveSource(ImporterProperties properties) {
        ImporterProperties.Sftp settings = properties.sftp();
        if (settings == null || !StringUtils.hasText(settings.host()) || !StringUtils.hasText(settings.username())) {
            throw new IllegalStateException("importer.sftp.host and importer.sftp.username are required");
        }

        DefaultSftpSessionFactory delegate = new DefaultSftpSessionFactory(false);
        delegate.setHost(settings.host());
        delegate.setPort(settings.port());
        delegate.setUser(settings.username());
        delegate.setAllowUnknownKeys(settings.allowUnknownHost());
        if (StringUtils.hasText(settings.password())) delegate.setPassword(settings.password());
        if (settings.privateKey() != null) delegate.setPrivateKey(new FileSystemResource(settings.privateKey()));
        if (StringUtils.hasText(settings.privateKeyPassphrase())) {
            delegate.setPrivateKeyPassphrase(settings.privateKeyPassphrase());
        }
        if (settings.knownHosts() != null) delegate.setKnownHostsResource(new FileSystemResource(settings.knownHosts()));

        SessionFactory<SftpClient.DirEntry> sessions = new CachingSessionFactory<>(delegate);
        return new SftpArchiveSource(sessions);
    }
}
