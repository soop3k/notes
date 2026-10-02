package pl.example.importer.application.service;

import pl.example.importer.domain.ImportRecord;
import pl.example.importer.domain.RecordType;
import java.util.Set;

public interface RecordMappingDefinition {
    RecordType type();
    Class<? extends ImportRecord> targetClass();
    Set<String> fields();
}
