package pl.example.importer.application.port.out;

import pl.example.importer.domain.ImportedRow;
import java.util.List;
import java.util.UUID;

public interface ImportedRowSink {
    void save(UUID executionId, String source, List<ImportedRow> rows);
}
