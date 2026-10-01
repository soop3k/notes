package pl.example.importer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class RowRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public RowRepository(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public void saveBatch(String source, List<Row> rows) {
        transaction.executeWithoutResult(status -> jdbc.batchUpdate(
                "insert into imported_row(source_file, row_number, payload, imported_at) values (?, ?, ?, ?)",
                rows, rows.size(), (PreparedStatement ps, Row row) -> {
                    ps.setString(1, source);
                    ps.setLong(2, row.number());
                    ps.setString(3, row.json());
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                }));
    }

    public record Row(long number, String json) { }
}
