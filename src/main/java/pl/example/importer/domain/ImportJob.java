package pl.example.importer.domain;

import pl.example.importer.application.port.in.ImportDefinition;

import java.time.Instant;
import java.util.UUID;

/** Durable unit of work created once for each request sent to the external system. */
public record ImportJob(UUID id, String requestId, ImportDefinition definition, Instant submittedAt) { }
