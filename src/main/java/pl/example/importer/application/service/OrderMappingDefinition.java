package pl.example.importer.application.service;

import pl.example.importer.domain.ImportRecord;
import pl.example.importer.domain.OrderRecord;
import pl.example.importer.domain.RecordType;
import java.util.Set;

public final class OrderMappingDefinition implements RecordMappingDefinition {
    @Override public RecordType type() { return RecordType.ORDER; }
    @Override public Class<? extends ImportRecord> targetClass() { return OrderRecord.class; }
    @Override public Set<String> fields() { return Set.of("orderNumber", "amount", "currency"); }
}
