package pl.example.importer.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pl.example.importer.ImporterProperties;
import pl.example.importer.adapter.out.jdbc.ImportedRowEntityMapper;
import pl.example.importer.adapter.out.jdbc.JdbcImportRepository;
import pl.example.importer.application.port.in.ImportArchiveUseCase;
import pl.example.importer.application.port.out.ArchiveSource;
import pl.example.importer.application.service.CsvRowImporter;
import pl.example.importer.application.service.JacksonCsvRecordMapper;
import pl.example.importer.application.service.CustomerMappingDefinition;
import pl.example.importer.application.service.OrderMappingDefinition;
import pl.example.importer.application.service.PathTemplateResolver;
import pl.example.importer.application.service.StreamingZipImportService;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class ImporterConfiguration {
    @Bean
    Clock importerClock(ImporterProperties properties) {
        return Clock.system(properties.zone());
    }

    @Bean
    JdbcImportRepository importRepository(JdbcTemplate jdbc, TransactionTemplate transactions,
                                           ObjectMapper objectMapper, Clock clock) {
        return new JdbcImportRepository(jdbc, transactions,
                new ImportedRowEntityMapper(objectMapper, clock));
    }

    @Bean
    CsvRowImporter csvRowImporter(JdbcImportRepository repository, ObjectMapper objectMapper) {
        return new CsvRowImporter(new CsvMapper(), objectMapper,
                new JacksonCsvRecordMapper(objectMapper,
                        List.of(new CustomerMappingDefinition(), new OrderMappingDefinition())), repository);
    }

    @Bean
    PathTemplateResolver pathTemplateResolver(Clock clock) {
        return new PathTemplateResolver(clock);
    }

    @Bean
    ImportArchiveUseCase importArchiveUseCase(ArchiveSource source, JdbcImportRepository repository,
            CsvRowImporter csvImporter, ObjectMapper objectMapper, PathTemplateResolver pathResolver,
            Clock clock) {
        return new StreamingZipImportService(source, repository, csvImporter, objectMapper,
                pathResolver, clock);
    }
}
