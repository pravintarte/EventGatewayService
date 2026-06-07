package com.cs.eventgateway.service.ledger;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventMetadataJsonConverterTest {

    private final EventMetadataJsonConverter converter = new EventMetadataJsonConverter(new ObjectMapper());

    @Test
    void write_whenMetadataIsPresent_serializesToJson() {
        String metadataJson = converter.write(Map.of("source", "mainframe-batch", "batchId", "B-9042"));

        assertThat(converter.structurallyEquals(metadataJson, Map.of("batchId", "B-9042", "source", "mainframe-batch")))
                .isTrue();
    }

    @Test
    void write_whenMetadataIsNull_serializesEmptyJsonObject() {
        assertThat(converter.write(null)).isEqualTo("{}");
    }

    @Test
    void write_whenObjectMapperFails_throwsIllegalArgumentException() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new TestJacksonException("boom"));
        EventMetadataJsonConverter failingConverter = new EventMetadataJsonConverter(objectMapper);

        assertThatThrownBy(() -> failingConverter.write(Map.of("source", "mainframe-batch")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metadata must be JSON serializable")
                .hasCauseInstanceOf(TestJacksonException.class);
    }

    @Test
    void read_whenJsonIsNullOrBlank_returnsEmptyMap() {
        assertThat(converter.read(null)).isEmpty();
        assertThat(converter.read("")).isEmpty();
        assertThat(converter.read("   ")).isEmpty();
    }

    @Test
    void read_whenJsonIsValid_returnsMetadataMap() {
        Map<String, Object> metadata = converter.read("{\"source\":\"mainframe-batch\",\"attempt\":2}");

        assertThat(metadata)
                .containsEntry("source", "mainframe-batch")
                .containsEntry("attempt", 2);
    }

    @Test
    void read_whenJsonIsInvalid_returnsEmptyMap() {
        assertThat(converter.read("{not-json")).isEmpty();
    }

    @Test
    void structurallyEquals_whenJsonObjectsHaveDifferentFieldOrder_returnsTrue() {
        assertThat(converter.structurallyEquals(
                "{\"source\":\"mainframe-batch\",\"batchId\":\"B-9042\"}",
                Map.of("batchId", "B-9042", "source", "mainframe-batch")
        )).isTrue();
    }

    @Test
    void structurallyEquals_whenStoredJsonIsNullOrBlankAndRequestMetadataIsNull_returnsTrue() {
        assertThat(converter.structurallyEquals(null, null)).isTrue();
        assertThat(converter.structurallyEquals("   ", null)).isTrue();
    }

    @Test
    void structurallyEquals_whenMetadataDiffers_returnsFalse() {
        assertThat(converter.structurallyEquals(
                "{\"source\":\"mainframe-batch\"}",
                Map.of("source", "mobile")
        )).isFalse();
    }

    @Test
    void structurallyEquals_whenStoredJsonIsInvalid_returnsFalse() {
        assertThat(converter.structurallyEquals("{not-json", Map.of("source", "mainframe-batch"))).isFalse();
    }

    @Test
    void safeMetadata_whenMetadataIsNull_returnsEmptyMapOtherwiseOriginalMap() {
        Map<String, Object> metadata = Map.of("source", "mainframe-batch");

        assertThat(converter.safeMetadata(null)).isEmpty();
        assertThat(converter.safeMetadata(metadata)).isSameAs(metadata);
    }

    private static final class TestJacksonException extends JacksonException {
        private TestJacksonException(String message) {
            super(message);
        }
    }
}
