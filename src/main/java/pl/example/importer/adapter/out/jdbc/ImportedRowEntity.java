package pl.example.importer.adapter.out.jdbc;

import java.time.Instant;
import java.util.UUID;

record ImportedRowEntity(UUID executionId, String source, long rowNumber, String recordType,
                         String businessKey, String payload, Instant importedAt) { }
