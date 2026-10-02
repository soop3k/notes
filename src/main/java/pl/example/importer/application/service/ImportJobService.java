package pl.example.importer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.example.importer.ImporterProperties;
import pl.example.importer.application.port.in.ImportArchiveUseCase;
import pl.example.importer.application.port.in.ImportDefinition;
import pl.example.importer.application.port.in.PollImportJobsUseCase;
import pl.example.importer.application.port.in.ScheduleImportJobUseCase;
import pl.example.importer.application.port.out.ArchiveSource;
import pl.example.importer.application.port.out.ImportJobStore;
import pl.example.importer.domain.ImportJob;

import java.time.Clock;
import java.util.UUID;

/** Coordinates durable, per-request polling jobs. It deliberately knows nothing about HTTP. */
public final class ImportJobService implements ScheduleImportJobUseCase, PollImportJobsUseCase {
    private static final Logger log = LoggerFactory.getLogger(ImportJobService.class);
    private final ImportJobStore jobs;
    private final ArchiveSource source;
    private final ImportArchiveUseCase importer;
    private final ImporterProperties.Polling polling;
    private final PathTemplateResolver pathResolver;
    private final Clock clock;

    public ImportJobService(ImportJobStore jobs, ArchiveSource source, ImportArchiveUseCase importer,
                            ImporterProperties.Polling polling, PathTemplateResolver pathResolver, Clock clock) {
        this.jobs = jobs;
        this.source = source;
        this.importer = importer;
        this.polling = polling;
        this.pathResolver = pathResolver;
        this.clock = clock;
    }

    @Override
    public UUID schedule(ScheduleImportJobCommand command) {
        UUID id = UUID.randomUUID();
        jobs.add(new ImportJob(id, command.requestId(), command.definition(), clock.instant()));
        log.info("Scheduled import job {} for request {} and file {}", id, command.requestId(),
                command.definition().pathTemplate());
        return id;
    }

    @Override
    public void poll() {
        jobs.claimDue(clock.instant(), polling.batchSize()).forEach(this::process);
    }

    private void process(ImportJob job) {
        try {
            String path = pathResolver.resolve(job.definition().pathTemplate());
            if (!source.exists(path)) {
                jobs.postpone(job.id(), clock.instant().plus(polling.fileCheckInterval()));
                log.debug("File {} for job {} is not available yet", path, job.id());
                return;
            }
            var d = job.definition();
            var resolvedDefinition = new ImportDefinition(d.name(), path,
                    d.schemaEntry(), d.csvEntry(), d.delimiter(), d.batchSize(), d.recordType(), d.headers());
            var result = importer.importArchive(new ImportArchiveUseCase.ImportCommand(resolvedDefinition));
            jobs.complete(job.id(), result.executionId(), clock.instant());
        } catch (Exception failure) {
            try {
                jobs.fail(job.id(), failure.getMessage(), clock.instant());
            } catch (RuntimeException trackingFailure) {
                failure.addSuppressed(trackingFailure);
            }
            log.error("Import job {} failed", job.id(), failure);
        }
    }
}
