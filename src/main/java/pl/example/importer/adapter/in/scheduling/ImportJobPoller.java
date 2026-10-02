package pl.example.importer.adapter.in.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import pl.example.importer.application.port.in.PollImportJobsUseCase;

public final class ImportJobPoller {
    private final PollImportJobsUseCase useCase;

    public ImportJobPoller(PollImportJobsUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelayString = "${importer.polling.fixed-delay:5s}")
    public void poll() {
        useCase.poll();
    }
}
