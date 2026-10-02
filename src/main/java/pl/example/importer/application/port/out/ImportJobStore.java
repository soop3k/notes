package pl.example.importer.application.port.out;

import pl.example.importer.domain.ImportJob;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ImportJobStore {
    void add(ImportJob job);
    List<ImportJob> claimDue(Instant now, int limit);
    void postpone(UUID jobId, Instant nextPollAt);
    void complete(UUID jobId, UUID executionId, Instant completedAt);
    void fail(UUID jobId, String reason, Instant failedAt);
}
