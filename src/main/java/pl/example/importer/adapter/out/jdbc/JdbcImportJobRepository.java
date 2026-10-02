package pl.example.importer.adapter.out.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pl.example.importer.application.port.in.ImportDefinition;
import pl.example.importer.application.port.out.ImportJobStore;
import pl.example.importer.domain.ImportJob;
import pl.example.importer.domain.RecordType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JDBC job queue. Conditional updates ensure that only one application instance claims a job. */
public final class JdbcImportJobRepository implements ImportJobStore {
    private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;

    public JdbcImportJobRepository(JdbcTemplate jdbc, TransactionTemplate transactions, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @Override
    public void add(ImportJob job) {
        try {
            var d = job.definition();
            jdbc.update("""
                    insert into import_job(id, request_id, import_name, source_file, schema_entry, csv_entry,
                      delimiter_char, import_batch_size, record_type, header_mapping, status, submitted_at, next_poll_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAITING_FOR_FILE', ?, ?)
                    """, job.id(), job.requestId(), d.name(), d.pathTemplate(), d.schemaEntry(), d.csvEntry(),
                    String.valueOf(d.delimiter()), d.batchSize(), d.recordType().name(), mapper.writeValueAsString(d.headers()),
                    Timestamp.from(job.submittedAt()), Timestamp.from(job.submittedAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize header mapping", exception);
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("An import job already exists for request " + job.requestId(), exception);
        }
    }

    @Override
    public List<ImportJob> claimDue(Instant now, int limit) {
        return transactions.execute(status -> {
            List<ImportJob> candidates = jdbc.query("""
                    select * from import_job where status = 'WAITING_FOR_FILE' and next_poll_at <= ?
                    order by next_poll_at, submitted_at limit ?
                    """, (rs, row) -> map(rs), Timestamp.from(now), limit);
            List<ImportJob> claimed = new ArrayList<>();
            for (ImportJob candidate : candidates) {
                int updated = jdbc.update("""
                        update import_job set status = 'PROCESSING', processing_started_at = ?
                        where id = ? and status = 'WAITING_FOR_FILE'
                        """, Timestamp.from(now), candidate.id());
                if (updated == 1) claimed.add(candidate);
            }
            return claimed;
        });
    }

    @Override
    public void postpone(UUID jobId, Instant nextPollAt) {
        transition(jobId, "update import_job set status='WAITING_FOR_FILE', next_poll_at=?, processing_started_at=null where id=? and status='PROCESSING'",
                Timestamp.from(nextPollAt), jobId);
    }

    @Override
    public void complete(UUID jobId, UUID executionId, Instant completedAt) {
        transition(jobId, "update import_job set status='COMPLETED', execution_id=?, completed_at=? where id=? and status='PROCESSING'",
                executionId, Timestamp.from(completedAt), jobId);
    }

    @Override
    public void fail(UUID jobId, String reason, Instant failedAt) {
        String message = reason == null ? "Unknown error" : reason.substring(0, Math.min(2000, reason.length()));
        transition(jobId, "update import_job set status='FAILED', error_message=?, completed_at=? where id=? and status='PROCESSING'",
                message, Timestamp.from(failedAt), jobId);
    }

    private void transition(UUID id, String sql, Object... arguments) {
        if (jdbc.update(sql, arguments) != 1) throw new IllegalStateException("Import job is not PROCESSING: " + id);
    }

    private ImportJob map(ResultSet rs) throws SQLException {
        try {
            Map<String, String> headers = mapper.readValue(rs.getString("header_mapping"), HEADERS_TYPE);
            var definition = new ImportDefinition(rs.getString("import_name"), rs.getString("source_file"),
                    rs.getString("schema_entry"), rs.getString("csv_entry"),
                    rs.getString("delimiter_char").charAt(0), rs.getInt("import_batch_size"),
                    RecordType.valueOf(rs.getString("record_type")), headers);
            return new ImportJob(rs.getObject("id", UUID.class), rs.getString("request_id"), definition,
                    rs.getTimestamp("submitted_at").toInstant());
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid header mapping stored for job " + rs.getString("id"), exception);
        }
    }
}
