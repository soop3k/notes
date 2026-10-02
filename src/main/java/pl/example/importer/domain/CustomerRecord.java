package pl.example.importer.domain;

public record CustomerRecord(String customerId, String fullName) implements ImportRecord {
    @Override public RecordType type() { return RecordType.CUSTOMER; }
    @Override public String businessKey() { return customerId; }
}
