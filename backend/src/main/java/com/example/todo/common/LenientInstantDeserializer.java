package com.example.todo.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public class LenientInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        text = text.trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            try {
                // Parse date-time without offset (e.g. 2026-10-08T00:00:00) as UTC
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            } catch (Exception ex) {
                try {
                    // Parse date-only string (e.g. 2026-10-08) as start of day in UTC
                    return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
                } catch (Exception ex2) {
                    throw new IOException("Failed to parse date into Instant: " + text, ex2);
                }
            }
        }
    }
}
