package pl.example.importer.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalArchiveSource implements ArchiveSource {
    @Override
    public InputStream open(String path) throws IOException {
        return Files.newInputStream(Path.of(path));
    }
}
