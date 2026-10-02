package pl.example.importer.domain;

/** Marker for typed business records produced by format-specific mappers. */
public sealed interface ImportRecord permits CustomerRecord, OrderRecord {
    RecordType type();
    String businessKey();
}
