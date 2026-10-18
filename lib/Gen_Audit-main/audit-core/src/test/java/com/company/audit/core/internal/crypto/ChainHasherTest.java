package com.company.audit.core.internal.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.HashValue;
import com.company.audit.core.api.PartitionContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChainHasherTest {

    private final ChainHasher chainHasher = new ChainHasher();

    @Test
    void genesisHashMatchesIndependentlyComputedValue() throws IOException, NoSuchAlgorithmException {
        PartitionContext partitionContext =
                new PartitionContext("test-partition", Instant.parse("2024-01-01T00:00:00Z"));

        HashValue genesis = chainHasher.computeGenesis(partitionContext);

        assertThat(genesis.hex()).isEqualTo(readExpectedHex("hash/genesis.expected.sha256"));
    }

    @Test
    void eventHashMatchesIndependentlyComputedValue() throws IOException, NoSuchAlgorithmException {
        HashValue prevHash = HashValue.of(sha256("prev"));
        HashValue payloadHash = HashValue.of(sha256("payload"));

        HashValue eventHash = chainHasher.computeEventHash(prevHash, payloadHash);

        assertThat(eventHash.hex()).isEqualTo(readExpectedHex("hash/event-hash.expected.sha256"));
    }

    private byte[] sha256(String input) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
    }

    private String readExpectedHex(String resourcePath) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }
}
