package pl.example.importer.persistence;

import pl.example.importer.domain.ImportedRow;
import java.util.List;

public interface ImportedRowWriter {
    void saveBatch(String source, List<ImportedRow> rows);
}
