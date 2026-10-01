package pl.example.importer.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import pl.example.importer.domain.ImportedRow;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcImportedRowRepository implements ImportedRowWriter {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcImportedRowRepository(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @Override
    public void saveBatch(String source, List<ImportedRow> rows) {
        Instant importedAt = Instant.now();
        transaction.executeWithoutResult(status -> jdbc.batchUpdate(
                "insert into imported_row(source_file, row_number, payload, imported_at) values (?, ?, ?, ?)",
                rows, rows.size(), (PreparedStatement statement, ImportedRow row) -> {
                    statement.setString(1, source);
                    statement.setLong(2, row.number());
                    statement.setString(3, row.json());
                    statement.setTimestamp(4, Timestamp.from(importedAt));
                }));
    }
}
