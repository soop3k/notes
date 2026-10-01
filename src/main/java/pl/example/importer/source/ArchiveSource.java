package pl.example.importer.source;

import java.io.IOException;
import java.io.InputStream;

/** Port used by the application layer to obtain an archive stream. */
public interface ArchiveSource {
    InputStream open(String path) throws IOException;
}
