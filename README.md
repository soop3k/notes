# Importer ZIP z kolejką jobów per request

Aplikacja Java 21 + Spring Boot obsługuje asynchroniczny etap po zakończeniu requestu biznesowego. Nie implementuje endpointu ani wysłania requestu do systemu zewnętrznego. Udostępnia port aplikacyjny `ScheduleImportJobUseCase`, który warstwa obsługująca request powinna wywołać dokładnie raz, przekazując identyfikator requestu, oczekiwaną ścieżkę pliku oraz kontrakt danego CSV.

## Przepływ

```text
request zakończony
  -> ScheduleImportJobUseCase (jeden trwały job per request)
  -> tabela import_job: WAITING_FOR_FILE
  -> @Scheduled poller
  -> ArchiveSource.exists(path)
       brak pliku: następna próba po file-check-interval
       plik istnieje: PROCESSING
  -> SFTP InputStream -> ZipInputStream -> schema.json + data.csv
  -> jeden wiersz CSV -> mapowanie nagłówków -> typowany rekord -> walidacja
  -> JDBC batch
  -> COMPLETED albo FAILED
```

Joby są trwałe, więc restart aplikacji nie usuwa oczekujących zadań. `request_id` jest unikalny i zabezpiecza przed utworzeniem dwóch jobów dla tego samego requestu. Claim jest wykonywany warunkowym `UPDATE`, dlatego dwóch uruchomionych workerów nie przetworzy równocześnie tego samego oczekującego joba. Błąd dostępnego pliku lub importu jest terminalny (`FAILED`); brak pliku nie jest błędem i powoduje ponowne sprawdzenie.

## Konfiguracja CSV per request

Każdy job przechowuje pełne `ImportDefinition`, a więc własną ścieżkę, separator, nazwy wpisów ZIP, rozmiar batcha, typ rekordu i mapę nagłówków. Mapowanie ma kierunek `pole obiektu -> nagłówek w pliku`:

```java
var definition = new ImportDefinition(
    "customer-export",
    "/exports/request-123.zip",
    "schema.json",
    "data.csv",
    ';',
    500,
    RecordType.CUSTOMER,
    Map.of("customerId", "client-code", "fullName", "display-name")
);

UUID jobId = scheduleImportJob.schedule(
    new ScheduleImportJobUseCase.ScheduleImportJobCommand("request-123", definition)
);
```

Inny request może wskazać zupełnie inne nazwy nagłówków, np. `customer_id` i `customer_name`. Konfiguracja jest zapisywana razem z jobem, a nie pobierana z globalnego YAML, dzięki czemu późniejsza zmiana konfiguracji nie zmienia już zleconego importu. Przykład zawiera typy `CustomerRecord` i `OrderRecord`; kolejny format dodaje się jako `ImportRecord` i `RecordMappingDefinition`.

## Streaming i transakcje

SFTP jest obsługiwane przez Spring Integration SFTP. `readRaw()` zwraca strumień, którego zamknięcie zwalnia również sesję. ZIP jest dekompresowany sekwencyjnie, a Jackson CSV materializuje tylko bieżący wiersz. W pamięci pozostaje schemat JSON i maksymalnie `batch-size` rekordów, nie cały plik. `schema.json` musi poprzedzać `data.csv` w archiwum.

Każdy batch jest osobną transakcją. `import_execution` audytuje właściwy import, `import_job` audytuje oczekiwanie na plik i wiąże job z execution, a `imported_row` zawiera wynik. Po awarii procesu job w `PROCESSING` wymaga świadomej operacji naprawczej; automatyczne przejęcie mogłoby powielić wcześniej zatwierdzone batche.

## Konfiguracja

```yaml
importer:
  mode: sftp # local w development
  zone: Europe/Warsaw
  polling:
    fixed-delay: 5s
    file-check-interval: 30s
    batch-size: 10
  sftp:
    host: sftp.example.org
    port: 22
    username: importer
    known-hosts: ${user.home}/.ssh/known_hosts
    allow-unknown-host: false
```

`fixed-delay` określa częstotliwość budzenia workera, `file-check-interval` odstęp ponownego sprawdzenia konkretnego pliku, a `batch-size` maksymalną liczbę jobów claimowanych w cyklu. Produkcyjnie należy weryfikować host przez `known_hosts`; dostępne jest uwierzytelnianie hasłem lub kluczem. Tryb `local` używa identycznego przepływu i nadaje się do developmentu/testów.

## Testy

```bash
mvn test
```

Testy obejmują streaming ZIP, walidację, różne nagłówki oraz scenariusz joba, który najpierw nie znajduje pliku, a po jego pojawieniu importuje go i kończy się statusem `COMPLETED`.
