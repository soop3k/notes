# Enterprise streaming ZIP importer

Przykładowa aplikacja Java 21 + Spring Boot importująca duży plik CSV z archiwum ZIP dostępnego lokalnie lub przez SFTP. ZIP i CSV **nie są kopiowane na dysk ani ładowane w całości do pamięci**: `InputStream` z adaptera trafia do `ZipInputStream`, a Jackson CSV tworzy po jednym typowanym obiekcie `ImportRecord`.

## Architektura

Projekt stosuje lekki wariant architektury heksagonalnej — bez frameworkowej ceremonii w logice biznesowej:

```text
adapter/in/startup
       │
       ▼
application/port/in/ImportArchiveUseCase
       │
       ▼
application/service/StreamingZipImportService
       ├── ArchiveSource ──► adapter/out/file | adapter/out/sftp
       ├── CsvRowImporter ─► ImportedRowSink ─► adapter/out/jdbc
       └── ImportExecutionStore ──────────────► adapter/out/jdbc
```

* `domain` — typowany kontrakt rekordu CSV i metadane wiersza, bez zależności od Springa;
* `application/port/in` — stabilne API przypadku użycia (`ImportCommand`, `ImportResult`);
* `application/port/out` — porty transportu, zapisu rekordów i audytu wykonań;
* `application/service` — orkiestracja ZIP-a, strumieniowe mapowanie CSV i walidacja;
* `adapter/out` — wymienne adaptery lokalne, SFTP i JDBC;
* `configuration` — jedyne miejsce składania implementacji i infrastruktury Springa.

`ImportRecord` jest wspólnym kontraktem dla typowanych formatów. Przykład zawiera `CustomerRecord` oraz `OrderRecord`. `JacksonCsvRecordMapper` wybiera klasę według `record-type` i stosuje mapowanie nagłówków danego joba. Typowany obiekt jest walidowany JSON Schema, a dopiero adapter JDBC mapuje go na `ImportedRowEntity`. Dodanie kolejnego formatu wymaga nowego rekordu domenowego i wpisu strategii — nie wymaga zmian w SFTP, ZIP-ie ani JDBC.

## Przepływ i pamięć

1. `ArchiveSource` otwiera zdalny lub lokalny strumień.
2. `ZipInputStream` sekwencyjnie odnajduje schemat oraz CSV.
3. `MappingIterator<Map<String,String>>` materializuje tylko surowy bieżący wiersz.
4. Mapper profilu normalizuje konfigurowalne nagłówki i natychmiast tworzy typowany `CustomerRecord` albo `OrderRecord`.
5. Rekord jest walidowany i dodawany do ograniczonej paczki.
6. Pełna paczka jest zapisywana przez JDBC batch w osobnej transakcji i usuwana z pamięci.

Zużycie pamięci zależy od buforów, schematu i `batch-size`, nie od rozmiaru CSV. Ponieważ ZIP jest sekwencyjny, `schema.json` musi znajdować się przed `data.csv`.

## Audyt i obsługa błędów

Każde uruchomienie otrzymuje UUID i wpis w `import_execution` z nazwą joba, stanem `RUNNING`, `COMPLETED` albo `FAILED`, czasami, liczbą rekordów i opisem błędu. Każdy zapisany rekord wskazuje `execution_id`, typ oraz klucz biznesowy. Schemat jest wersjonowany migracją Flyway, co pozwala bezpiecznie rozwijać bazę poza H2. Ułatwia to diagnostykę oraz późniejsze wdrożenie retry lub idempotencji.

Paczki są niezależnymi transakcjami. Jeżeli błąd wystąpi po zapisaniu wcześniejszych paczek, pozostają one przypisane do wykonania oznaczonego `FAILED`. Jest to świadoma semantyka odpowiednia dla dużych importów; proces naprawczy może jednoznacznie usunąć lub wznowić dane danego UUID.

## Wiele plików i różne nagłówki

Każdy wpis `importer.jobs` jest niezależnym profilem: ma własną ścieżkę, wpisy ZIP, separator, rozmiar paczki, typ rekordu i mapę nagłówków. Klucz mapy `headers` to pole docelowego obiektu Javy, a wartość to nagłówek w konkretnym pliku:

```yaml
importer:
  mode: sftp
  run-on-startup: true
  jobs:
    customers-pl:
      enabled: true
      path: /exports/customers-{date}.zip
      record-type: customer
      headers:
        customerId: numer_klienta
        fullName: nazwa_klienta
    orders-en:
      enabled: true
      path: /exports/orders-{date}.zip
      delimiter: ";"
      batch-size: 1000
      record-type: order
      headers:
        orderNumber: order_no
        amount: gross_amount
        currency: currency_code
```

Jeżeli plik używa nazw identycznych z polami obiektu (`customerId`, `fullName`), mapa `headers` może być pusta. JSON Schema opisuje już nazwy i typy **docelowego obiektu** po normalizacji nagłówków. Brak skonfigurowanej kolumny, nieznane pole docelowe lub niepoprawny typ kończy import czytelnym błędem i stanem `FAILED`.

## Konfiguracja i uruchomienie

Lokalnie można nadpisać ścieżkę wybranego joba:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--importer.run-on-startup=true --importer.mode=local --importer.jobs.customers.path=/tmp/import.zip"
```

Dla SFTP ustaw `importer.mode=sftp` oraz `importer.sftp.host`, `username` i hasło albo klucz prywatny. W produkcji należy pozostawić `allow-unknown-host=false` i wskazać `known-hosts`. Wspierane są `{date}` oraz `{datetime}` w strefie `importer.zone`.

Najważniejsze ustawienia joba:

| Klucz | Znaczenie | Domyślnie |
|---|---|---|
| `enabled` | uruchomienie joba przy starcie | `false` |
| `path` | ścieżka lub szablon | wymagane |
| `record-type` | `customer` albo `order` | wymagane |
| `headers` | pole obiektu → nagłówek pliku | nazwa pola |
| `schema-entry` | wpis JSON Schema w ZIP | `schema.json` |
| `csv-entry` | wpis CSV w ZIP | `data.csv` |
| `delimiter` | separator CSV | `,` |
| `batch-size` | rekordy w transakcji | `500` |

## Testy

```bash
mvn test
mvn package
```

Test integracyjny obejmuje poprawny import i utrwalenie stanu `COMPLETED` oraz odrzucenie niepoprawnego rekordu i utrwalenie stanu `FAILED`.
