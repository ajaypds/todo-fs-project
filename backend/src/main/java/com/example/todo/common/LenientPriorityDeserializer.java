package com.example.todo.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class LenientPriorityDeserializer extends JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            int val = p.getIntValue();
            if (val >= 1 && val <= 4) {
                return val;
            }
            return 4;
        }

        String text = p.getText();
        if (text == null || text.isBlank()) {
            return 4;
        }
        text = text.trim().toLowerCase();

        try {
            int val = Integer.parseInt(text);
            if (val >= 1 && val <= 4) {
                return val;
            }
            return 4;
        } catch (NumberFormatException ignored) {
        }

        return switch (text) {
            case "urgent", "critical", "p1", "highest" -> 1;
            case "high", "p2" -> 2;
            case "medium", "normal", "moderate", "p3" -> 3;
            case "low", "p4", "lowest" -> 4;
            default -> 4;
        };
    }
}
