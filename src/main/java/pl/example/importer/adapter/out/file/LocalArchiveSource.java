package pl.example.importer.adapter.out.file;

import pl.example.importer.application.port.out.ArchiveSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalArchiveSource implements ArchiveSource {
    @Override
    public boolean exists(String path) {
        return Files.isRegularFile(Path.of(path));
    }

    @Override
    public InputStream open(String path) throws IOException {
        return Files.newInputStream(Path.of(path));
    }
}
