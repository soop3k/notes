# Streaming ZIP importer (Java 21 + Spring Boot)

Aplikacja **nie zapisuje ZIP-a ani CSV na dysku**. Czyta strumień z lokalnego pliku albo bezpośrednio z SFTP, przechodzi sekwencyjnie po wpisach ZIP, waliduje każdy rekord CSV schematem JSON Schema i zapisuje rekordy porcjami do H2.

## Ważne założenie formatu ZIP

ZIP jest formatem sekwencyjnym. Bez pobrania/pliku tymczasowego nie można cofnąć strumienia, dlatego `schema.json` musi znajdować się **przed** `data.csv` w archiwum. Nazwy obu wpisów są konfigurowalne. Wartości CSV są mapowane według nagłówka na obiekt JSON (wszystkie wartości są napisami), np. `id,name` daje `{"id":"1","name":"Ala"}`.

## Uruchomienie lokalne

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--importer.run-on-startup=true --importer.mode=local --importer.path=/tmp/import.zip"
```

## SFTP

```bash
export SFTP_PASSWORD='sekret'
mvn spring-boot:run -Dspring-boot.run.arguments="\
 --importer.run-on-startup=true \
 --importer.mode=sftp \
 --importer.path=/exports/import-{date}.zip \
 --importer.sftp.host=sftp.example.org \
 --importer.sftp.port=22 \
 --importer.sftp.username=importer \
 --importer.sftp.password=${SFTP_PASSWORD}"
```

Domyślnie weryfikowany jest klucz serwera z `~/.ssh/known_hosts`. Klucz prywatny można wskazać przez `importer.sftp.private-key`. Opcja `allow-unknown-host=true` jest przeznaczona wyłącznie do lokalnego developmentu. W ścieżce można użyć `{date}` (`yyyy-MM-dd`) albo `{datetime}` (`yyyyMMdd-HHmmss`); strefę ustala `importer.zone`.

## Zachowanie i niezawodność

* `batch-size` ogranicza pamięć i liczbę round-tripów do bazy (domyślnie 500).
* Każda porcja jest osobną transakcją. Błąd późniejszego rekordu nie wycofa wcześniejszych porcji; do produkcji warto dodać identyfikator uruchomienia i strategię idempotencji zależną od domeny.
* Pamięć to w przybliżeniu bufor wejścia + bieżący wpis ZIP + jedna porcja rekordów, a nie rozmiar 100 MB pliku.
* Import startuje tylko przy `importer.run-on-startup=true`, więc testy/development nie łączą się przypadkiem z SFTP.
* H2 zapisuje dane do `./data/imports`. W produkcji wystarczy zmienić `spring.datasource.*`, np. na PostgreSQL i dodać jego sterownik.

## Testy i budowanie

```bash
mvn test
mvn package
```
