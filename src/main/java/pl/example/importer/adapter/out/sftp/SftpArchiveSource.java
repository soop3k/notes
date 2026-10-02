package pl.example.importer.adapter.out.sftp;

import pl.example.importer.application.port.out.ArchiveSource;

import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Streams a remote file and returns the Spring Integration session on close. */
public final class SftpArchiveSource implements ArchiveSource {
    private final SessionFactory<SftpClient.DirEntry> sessions;

    public SftpArchiveSource(SessionFactory<SftpClient.DirEntry> sessions) {
        this.sessions = sessions;
    }

    @Override
    public InputStream open(String path) throws IOException {
        Session<SftpClient.DirEntry> session = sessions.getSession();
        try {
            InputStream remoteStream = session.readRaw(path);
            return new FilterInputStream(remoteStream) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        session.close();
                    }
                }
            };
        } catch (RuntimeException | IOException exception) {
            session.close();
            throw exception;
        }
    }
}
