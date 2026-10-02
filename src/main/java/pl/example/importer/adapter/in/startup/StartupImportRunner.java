package pl.example.importer.adapter.in.startup;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.example.importer.ImporterProperties;
import pl.example.importer.application.port.in.ImportArchiveUseCase;
import pl.example.importer.application.port.in.ImportDefinition;

@Component
@ConditionalOnProperty(prefix = "importer", name = "run-on-startup", havingValue = "true")
public final class StartupImportRunner implements ApplicationRunner {
    private final ImportArchiveUseCase useCase;
    private final ImporterProperties properties;

    public StartupImportRunner(ImportArchiveUseCase useCase, ImporterProperties properties) {
        this.useCase = useCase;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        properties.jobs().forEach((name, job) -> {
            if (job.enabled()) useCase.importArchive(new ImportArchiveUseCase.ImportCommand(
                    new ImportDefinition(name, job.path(), job.schemaEntry(), job.csvEntry(),
                            job.delimiter(), job.batchSize(), job.recordType(), job.headers())));
        });
    }
}
