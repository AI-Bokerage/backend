package com.company.audit.core.internal.crypto;

import com.company.audit.core.api.exception.CanonicalizationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts an event payload into a deterministic, canonical JSON string so that byte-identical
 * payloads always hash to the same value regardless of key insertion order, text normalization
 * form, or number formatting.
 */
public final class CanonicalJsonSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a new serializer.
     */
    public CanonicalJsonSerializer() {
    }

    /**
     * Canonicalizes the given payload into a compact JSON string.
     *
     * <p>Object keys are sorted using plain {@link String#compareTo}, array order is preserved,
     * text is normalized to NFC, and numbers are rendered in a single canonical plain-string
     * form with no whitespace anywhere in the output.
     *
     * @param payload the payload to canonicalize
     * @return the canonical JSON representation
     * @throws CanonicalizationException if the payload cannot be canonicalized
     */
    public String canonicalize(Map<String, Object> payload) {
        try {
            JsonNode tree = objectMapper.valueToTree(payload);
            StringBuilder out = new StringBuilder();
            write(tree, out);
            return out.toString();
        } catch (RuntimeException e) {
            throw new CanonicalizationException("Failed to canonicalize payload", e);
        }
    }

    private void write(JsonNode node, StringBuilder out) {
        if (node.isObject()) {
            writeObject(node, out);
        } else if (node.isArray()) {
            writeArray(node, out);
        } else if (node.isTextual()) {
            writeString(Normalizer.normalize(node.textValue(), Normalizer.Form.NFC), out);
        } else if (node.isNumber()) {
            out.append(canonicalNumber(node));
        } else if (node.isBoolean()) {
            out.append(node.booleanValue());
        } else if (node.isNull()) {
            out.append("null");
        } else {
            writeString(node.asText(), out);
        }
    }

    private void writeObject(JsonNode node, StringBuilder out) {
        TreeMap<String, JsonNode> sorted = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            sorted.put(entry.getKey(), entry.getValue());
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, JsonNode> entry : sorted.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(entry.getKey(), out);
            out.append(':');
            write(entry.getValue(), out);
        }
        out.append('}');
    }

    private void writeArray(JsonNode node, StringBuilder out) {
        out.append('[');
        boolean first = true;
        for (JsonNode element : node) {
            if (!first) {
                out.append(',');
            }
            first = false;
            write(element, out);
        }
        out.append(']');
    }

    private void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private String canonicalNumber(JsonNode node) {
        BigDecimal value = node.decimalValue().stripTrailingZeros();
        String plain = value.toPlainString();
        if (plain.equals("-0")) {
            plain = "0";
        }
        return plain;
    }
}
