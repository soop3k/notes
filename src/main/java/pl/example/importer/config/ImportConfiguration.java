package pl.example.importer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.example.importer.ImporterProperties;
import pl.example.importer.application.CsvImportProcessor;
import pl.example.importer.application.PathTemplateResolver;
import pl.example.importer.application.ZipImportService;
import pl.example.importer.persistence.ImportedRowWriter;
import pl.example.importer.source.ArchiveSource;

@Configuration(proxyBeanMethods = false)
public class ImportConfiguration {
    @Bean PathTemplateResolver pathTemplateResolver(ImporterProperties properties) {
        return new PathTemplateResolver(properties.zone());
    }

    @Bean CsvImportProcessor csvImportProcessor(ObjectMapper mapper, ImportedRowWriter writer,
                                                  ImporterProperties properties) {
        return new CsvImportProcessor(mapper, writer, properties.delimiter(), properties.batchSize());
    }

    @Bean ZipImportService zipImportService(ImporterProperties properties, ArchiveSource source,
                                             CsvImportProcessor processor, ObjectMapper mapper,
                                             PathTemplateResolver resolver) {
        return new ZipImportService(properties, source, processor, mapper, resolver);
    }
}
