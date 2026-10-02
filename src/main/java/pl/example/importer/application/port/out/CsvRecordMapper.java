package pl.example.importer.application.port.out;

import pl.example.importer.domain.ImportRecord;
import pl.example.importer.domain.RecordType;
import java.util.Map;

public interface CsvRecordMapper {
    ImportRecord map(RecordType recordType, Map<String, String> headers, Map<String, String> row);
}
