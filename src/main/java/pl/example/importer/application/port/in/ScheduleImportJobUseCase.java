package pl.example.importer.application.port.in;

import java.util.UUID;

public interface ScheduleImportJobUseCase {
    UUID schedule(ScheduleImportJobCommand command);

    record ScheduleImportJobCommand(String requestId, ImportDefinition definition) {
        public ScheduleImportJobCommand {
            if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId must not be blank");
            if (definition == null) throw new IllegalArgumentException("definition must not be null");
        }
    }
}
