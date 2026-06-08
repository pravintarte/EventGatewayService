package com.cs.eventgateway.dto.event;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Deserializes inbound event timestamps only when the supplied offset is UTC.
 */
public class UtcInstantDeserializer extends StdDeserializer<Instant> {

    /**
     * Creates a deserializer that produces {@link Instant} values for UTC timestamps.
     */
    public UtcInstantDeserializer() {
        super(Instant.class);
    }

    /**
     * Parses an inbound ISO-8601 timestamp and rejects any non-UTC offset.
     *
     * <p>The public event contract requires UTC timestamps so the gateway can
     * store and compare business event times without timezone ambiguity. The
     * method accepts both {@code Z} and {@code +00:00} offsets, then normalizes
     * the value to {@link Instant} for persistence and response mapping.</p>
     *
     * @param parser Jackson parser positioned on the timestamp field
     * @param context active deserialization context
     * @return parsed UTC instant
     * @throws JacksonException when the value is blank, malformed, or not UTC
     */
    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            throw InvalidFormatException.from(
                    parser,
                    "eventTimestamp must be an ISO-8601 timestamp with a UTC timezone.",
                    value,
                    Instant.class
            );
        }

        try {
            OffsetDateTime timestamp = OffsetDateTime.parse(value);
            if (!timestamp.getOffset().equals(ZoneOffset.UTC)) {
                throw InvalidFormatException.from(
                        parser,
                        "eventTimestamp must use UTC timezone offset Z or +00:00.",
                        value,
                        Instant.class
                );
            }
            return timestamp.toInstant();
        } catch (DateTimeParseException ex) {
            throw InvalidFormatException.from(
                    parser,
                    "eventTimestamp must be an ISO-8601 timestamp with a UTC timezone.",
                    value,
                    Instant.class
            );
        }
    }
}
