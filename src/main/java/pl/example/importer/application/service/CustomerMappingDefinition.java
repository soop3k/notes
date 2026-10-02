package pl.example.importer.application.service;

import pl.example.importer.domain.CustomerRecord;
import pl.example.importer.domain.ImportRecord;
import pl.example.importer.domain.RecordType;
import java.util.Set;

public final class CustomerMappingDefinition implements RecordMappingDefinition {
    @Override public RecordType type() { return RecordType.CUSTOMER; }
    @Override public Class<? extends ImportRecord> targetClass() { return CustomerRecord.class; }
    @Override public Set<String> fields() { return Set.of("customerId", "fullName"); }
}
