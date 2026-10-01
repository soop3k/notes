package pl.example.importer;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "importer", name = "run-on-startup", havingValue = "true")
class StartupImport implements ApplicationRunner {
    private final ZipImportService service;
    StartupImport(ZipImportService service) { this.service = service; }
    @Override public void run(ApplicationArguments args) throws Exception { service.importArchive(); }
}
