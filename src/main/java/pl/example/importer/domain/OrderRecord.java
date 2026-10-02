package pl.example.importer.domain;

import java.math.BigDecimal;

public record OrderRecord(String orderNumber, BigDecimal amount, String currency) implements ImportRecord {
    @Override public RecordType type() { return RecordType.ORDER; }
    @Override public String businessKey() { return orderNumber; }
}
