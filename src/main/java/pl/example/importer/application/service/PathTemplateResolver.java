package pl.example.importer.application.service;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class PathTemplateResolver {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final Clock clock;

    public PathTemplateResolver(Clock clock) {
        this.clock = clock;
    }

    public String resolve(String template) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        return template.replace("{date}", now.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .replace("{datetime}", now.format(DATE_TIME));
    }
}
