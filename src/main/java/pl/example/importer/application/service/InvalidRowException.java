package pl.example.importer.application.service;

import java.io.IOException;

public final class InvalidRowException extends IOException {
    private final long rowNumber;

    public InvalidRowException(long rowNumber, Object errors) {
        super("CSV row " + rowNumber + " does not satisfy JSON Schema: " + errors);
        this.rowNumber = rowNumber;
    }

    public long rowNumber() {
        return rowNumber;
    }
}
