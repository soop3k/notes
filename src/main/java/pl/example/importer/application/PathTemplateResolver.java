package pl.example.importer.application;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class PathTemplateResolver {
    private final ZoneId zone;
    private final Clock clock;

    public PathTemplateResolver(ZoneId zone) {
        this(zone, Clock.system(zone));
    }

    PathTemplateResolver(ZoneId zone, Clock clock) {
        this.zone = zone;
        this.clock = clock;
    }

    public String resolve(String pattern) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        return pattern.replace("{date}", now.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .replace("{datetime}", now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
    }
}
