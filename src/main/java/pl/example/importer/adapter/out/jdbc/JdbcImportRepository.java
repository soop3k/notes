package pl.example.importer.adapter.out.jdbc;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pl.example.importer.application.port.out.ImportExecutionStore;
import pl.example.importer.application.port.out.ImportedRowSink;
import pl.example.importer.domain.ImportedRow;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JdbcImportRepository implements ImportedRowSink, ImportExecutionStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ImportedRowEntityMapper mapper;

    public JdbcImportRepository(JdbcTemplate jdbc, TransactionTemplate transactions,
                                ImportedRowEntityMapper mapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @Override
    public void save(UUID executionId, String source, List<ImportedRow> rows) {
        List<ImportedRowEntity> entities = rows.stream()
                .map(row -> mapper.toEntity(executionId, source, row)).toList();
        transactions.executeWithoutResult(status -> jdbc.batchUpdate("""
                insert into imported_row(execution_id, source_file, row_number, record_type, business_key, payload, imported_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, entities, entities.size(), this::bind));
    }

    @Override
    public void started(UUID id, String importName, String source, Instant at) {
        jdbc.update("insert into import_execution(id, import_name, source_file, status, started_at) values (?, ?, ?, 'RUNNING', ?)",
                id, importName, source, Timestamp.from(at));
    }

    @Override
    public void completed(UUID id, long rows, Instant at) {
        updateTerminalState(id, "COMPLETED", rows, null, at);
    }

    @Override
    public void failed(UUID id, String reason, Instant at) {
        updateTerminalState(id, "FAILED", null, truncate(reason), at);
    }

    private void updateTerminalState(UUID id, String status, Long rows, String error, Instant at) {
        int updated = jdbc.update("""
                update import_execution set status = ?, imported_rows = ?, error_message = ?, completed_at = ?
                where id = ? and status = 'RUNNING'
                """, status, rows, error, Timestamp.from(at), id);
        if (updated != 1) throw new DataAccessException("Execution is not in RUNNING state: " + id) { };
    }

    private void bind(PreparedStatement statement, ImportedRowEntity entity) throws java.sql.SQLException {
        statement.setObject(1, entity.executionId());
        statement.setString(2, entity.source());
        statement.setLong(3, entity.rowNumber());
        statement.setString(4, entity.recordType());
        statement.setString(5, entity.businessKey());
        statement.setString(6, entity.payload());
        statement.setTimestamp(7, Timestamp.from(entity.importedAt()));
    }

    private String truncate(String reason) {
        if (reason == null) return "Unknown error";
        return reason.substring(0, Math.min(reason.length(), 2000));
    }
}
