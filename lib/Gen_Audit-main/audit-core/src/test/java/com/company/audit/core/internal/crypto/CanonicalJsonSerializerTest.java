package com.company.audit.core.internal.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalJsonSerializerTest {

    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void nestedObjectAndArraySortKeysAndPreserveArrayOrder() throws IOException {
        Map<String, Object> input = readInputAsMap("canonical/nested-object.input.json");
        String expected = readExpectedText("canonical/nested-object.expected.json");

        assertThat(serializer.canonicalize(input)).isEqualTo(expected);
    }

    @Test
    void precomposedAndDecomposedUnicodeCanonicalizeToIdenticalNfcForm() throws IOException {
        Map<String, Object> input = readInputAsMap("canonical/unicode-nfc.input.json");
        String expected = readExpectedText("canonical/unicode-nfc.expected.json");

        assertThat(serializer.canonicalize(input)).isEqualTo(expected);
    }

    @Test
    void decimalWithTrailingZeroCanonicalizesWithoutThem() {
        assertThat(serializer.canonicalize(Map.of("v", 1.50))).isEqualTo("{\"v\":1.5}");
    }

    @Test
    void integralNumberCanonicalizesWithoutDecimalPoint() {
        assertThat(serializer.canonicalize(Map.of("v", 100))).isEqualTo("{\"v\":100}");
    }

    @Test
    void negativeZeroCanonicalizesToZero() {
        assertThat(serializer.canonicalize(Map.of("v", -0.0))).isEqualTo("{\"v\":0}");
    }

    @Test
    void emptyPayloadCanonicalizesToEmptyObject() {
        assertThat(serializer.canonicalize(Map.of())).isEqualTo("{}");
    }

    @Test
    void multiByteScriptsAndEmojiRoundTripUnchanged() {
        assertThat(serializer.canonicalize(Map.of("v", "漢字 مرحبا 🎉")))
                .isEqualTo("{\"v\":\"漢字 مرحبا 🎉\"}");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readInputAsMap(String resourcePath) throws IOException {
        try (InputStream in = getResource(resourcePath)) {
            return objectMapper.readValue(in, Map.class);
        }
    }

    private String readExpectedText(String resourcePath) throws IOException {
        try (InputStream in = getResource(resourcePath)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }

    private InputStream getResource(String resourcePath) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Missing test resource: " + resourcePath);
        }
        return in;
    }
}
