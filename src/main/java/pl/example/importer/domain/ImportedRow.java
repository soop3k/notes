package pl.example.importer.domain;

public record ImportedRow(long number, ImportRecord data) {
    public ImportedRow {
        if (number < 1) throw new IllegalArgumentException("Row number must be positive");
        if (data == null) throw new IllegalArgumentException("Row data must not be null");
    }
}
