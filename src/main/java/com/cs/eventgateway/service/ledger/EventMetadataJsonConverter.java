package com.cs.eventgateway.service.ledger;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Converts event metadata between request maps and persisted JSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventMetadataJsonConverter {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * Serializes metadata into JSON for ledger storage.
     *
     * @param metadata nullable request metadata
     * @return metadata JSON
     */
    public String write(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(safeMetadata(metadata));
        } catch (JacksonException ex) {
            log.warn("Unable to serialize event metadata for ledger storage", ex);
            throw new IllegalArgumentException("metadata must be JSON serializable", ex);
        }
    }

    /**
     * Reads persisted metadata JSON into the public API shape.
     *
     * @param metadataJson metadata JSON stored in the ledger
     * @return metadata map, or an empty map when absent or unreadable
     */
    public Map<String, Object> read(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(metadataJson, METADATA_TYPE);
        } catch (JacksonException ex) {
            log.warn("Unable to read stored metadata JSON", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Compares stored metadata JSON with request metadata structurally.
     *
     * @param existingMetadataJson persisted metadata JSON
     * @param requestMetadata request metadata
     * @return true when both values represent the same JSON object
     */
    public boolean structurallyEquals(String existingMetadataJson, Map<String, Object> requestMetadata) {
        try {
            JsonNode existing = objectMapper.readTree(
                    existingMetadataJson == null || existingMetadataJson.isBlank() ? "{}" : existingMetadataJson
            );
            JsonNode requested = objectMapper.valueToTree(safeMetadata(requestMetadata));
            return Objects.equals(existing, requested);
        } catch (JacksonException ex) {
            log.warn("Unable to compare metadata JSON for idempotency", ex);
            return false;
        }
    }

    /**
     * Normalizes nullable request metadata to a single empty-map representation.
     *
     * @param metadata nullable request metadata
     * @return original metadata or an empty map
     */
    public Map<String, Object> safeMetadata(Map<String, Object> metadata) {
        return metadata == null ? Collections.emptyMap() : metadata;
    }
}
