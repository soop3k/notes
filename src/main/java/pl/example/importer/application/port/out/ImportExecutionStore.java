package pl.example.importer.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface ImportExecutionStore {
    void started(UUID executionId, String importName, String source, Instant startedAt);
    void completed(UUID executionId, long importedRows, Instant completedAt);
    void failed(UUID executionId, String reason, Instant failedAt);
}
