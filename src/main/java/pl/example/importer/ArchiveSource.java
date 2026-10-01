package pl.example.importer;

import java.io.IOException;
import java.io.InputStream;

public interface ArchiveSource {
    InputStream open(String path) throws IOException;
}
