package pl.example.importer;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class SftpArchiveSource implements ArchiveSource {
    private final ImporterProperties.Sftp configuration;

    public SftpArchiveSource(ImporterProperties.Sftp configuration) {
        this.configuration = configuration;
    }

    @Override public InputStream open(String path) throws IOException {
        if (configuration == null || configuration.host() == null || configuration.username() == null) {
            throw new IllegalStateException("importer.sftp.host and importer.sftp.username are required");
        }
        SSHClient ssh = new SSHClient();
        try {
            if (configuration.allowUnknownHost()) {
                ssh.addHostKeyVerifier(new PromiscuousVerifier());
            } else {
                if (configuration.knownHosts() == null) {
                    throw new IllegalStateException("Set importer.sftp.known-hosts or explicitly allow an unknown host");
                }
                ssh.loadKnownHosts(configuration.knownHosts().toFile());
            }
            ssh.connect(configuration.host(), configuration.port());
            if (configuration.privateKey() != null) {
                ssh.authPublickey(configuration.username(), ssh.loadKeys(configuration.privateKey().toString()));
            } else {
                ssh.authPassword(configuration.username(), configuration.password());
            }
            SFTPClient sftp = ssh.newSFTPClient();
            RemoteFile remote = sftp.open(path);
            InputStream stream = remote.new RemoteFileInputStream();
            return new FilterInputStream(stream) {
                @Override public void close() throws IOException {
                    IOException failure = null;
                    try { super.close(); } catch (IOException e) { failure = e; }
                    try { remote.close(); } catch (IOException e) { if (failure == null) failure = e; }
                    try { sftp.close(); } catch (IOException e) { if (failure == null) failure = e; }
                    try { ssh.disconnect(); } catch (IOException e) { if (failure == null) failure = e; }
                    try { ssh.close(); } catch (IOException e) { if (failure == null) failure = e; }
                    if (failure != null) throw failure;
                }
            };
        } catch (Exception e) {
            try { ssh.close(); } catch (IOException ignored) { }
            if (e instanceof IOException io) throw io;
            throw new IOException("Cannot open SFTP file " + path, e);
        }
    }
}
