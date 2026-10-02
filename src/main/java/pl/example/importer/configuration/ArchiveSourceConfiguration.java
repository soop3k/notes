package pl.example.importer.configuration;

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
import pl.example.importer.adapter.out.file.LocalArchiveSource;
import pl.example.importer.adapter.out.sftp.SftpArchiveSource;
import pl.example.importer.application.port.out.ArchiveSource;

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
        requireSftpSettings(settings);
        DefaultSftpSessionFactory delegate = new DefaultSftpSessionFactory(false);
        delegate.setHost(settings.host());
        delegate.setPort(settings.port());
        delegate.setUser(settings.username());
        delegate.setAllowUnknownKeys(settings.allowUnknownHost());
        if (StringUtils.hasText(settings.password())) delegate.setPassword(settings.password());
        if (settings.privateKey() != null) delegate.setPrivateKey(new FileSystemResource(settings.privateKey()));
        if (StringUtils.hasText(settings.privateKeyPassphrase())) delegate.setPrivateKeyPassphrase(settings.privateKeyPassphrase());
        if (settings.knownHosts() != null) delegate.setKnownHostsResource(new FileSystemResource(settings.knownHosts()));
        SessionFactory<SftpClient.DirEntry> sessions = new CachingSessionFactory<>(delegate);
        return new SftpArchiveSource(sessions);
    }

    private void requireSftpSettings(ImporterProperties.Sftp settings) {
        if (settings == null || !StringUtils.hasText(settings.host()) || !StringUtils.hasText(settings.username())) {
            throw new IllegalStateException("SFTP mode requires importer.sftp.host and importer.sftp.username");
        }
        if (!settings.allowUnknownHost() && settings.knownHosts() == null) {
            throw new IllegalStateException("Configure known-hosts or explicitly allow unknown hosts");
        }
    }
}
