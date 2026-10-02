package pl.example.importer.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface ImportArchiveUseCase {
    ImportResult importArchive(ImportCommand command);

    record ImportCommand(ImportDefinition definition) {
        public ImportCommand {
            if (definition == null) throw new IllegalArgumentException("definition must not be null");
        }
    }

    record ImportResult(UUID executionId, String importName, String source, long importedRows,
                        Instant startedAt, Instant completedAt) { }
}
