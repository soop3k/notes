package pl.example.importer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.example.importer.domain.CustomerRecord;
import pl.example.importer.domain.OrderRecord;
import pl.example.importer.domain.RecordType;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonCsvRecordMapperTest {
    private final JacksonCsvRecordMapper mapper = new JacksonCsvRecordMapper(new ObjectMapper(),
            java.util.List.of(new CustomerMappingDefinition(), new OrderMappingDefinition()));

    @Test
    void mapsConfiguredCustomerHeadersToTypedRecord() {
        var result = mapper.map(RecordType.CUSTOMER,
                Map.of("customerId", "client_no", "fullName", "display_name"),
                Map.of("client_no", "C-7", "display_name", "Ada"));

        assertThat(result).isEqualTo(new CustomerRecord("C-7", "Ada"));
    }

    @Test
    void mapsAnotherFileFormatAndConvertsFieldTypes() {
        var result = mapper.map(RecordType.ORDER,
                Map.of("orderNumber", "order_no", "amount", "total", "currency", "ccy"),
                Map.of("order_no", "O-1", "total", "12.50", "ccy", "PLN"));

        assertThat(result).isEqualTo(new OrderRecord("O-1", new BigDecimal("12.50"), "PLN"));
    }

    @Test
    void rejectsAConfiguredHeaderMissingFromTheFile() {
        assertThatThrownBy(() -> mapper.map(RecordType.CUSTOMER,
                Map.of("customerId", "missing", "fullName", "name"), Map.of("name", "Ada")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }
}
