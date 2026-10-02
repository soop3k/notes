package pl.example.importer.application.service;

public final class ImportException extends RuntimeException {
    private final String source;

    public ImportException(String source, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    public String source() {
        return source;
    }
}
