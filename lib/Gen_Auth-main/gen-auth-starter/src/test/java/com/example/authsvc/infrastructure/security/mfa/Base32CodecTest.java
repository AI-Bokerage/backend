package com.example.authsvc.infrastructure.security.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class Base32CodecTest {

    @Test
    void encode_knownVector_matchesRfc4648() {
        // RFC 4648 §10 test vectors
        assertThat(Base32Codec.encode("".getBytes(StandardCharsets.US_ASCII))).isEqualTo("");
        assertThat(Base32Codec.encode("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY");
        assertThat(Base32Codec.encode("fo".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXQ");
        assertThat(Base32Codec.encode("foo".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6");
        assertThat(Base32Codec.encode("foob".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YQ");
        assertThat(Base32Codec.encode("fooba".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTB");
        assertThat(Base32Codec.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
    }

    @Test
    void decode_knownVector_matchesRfc4648() {
        assertThat(Base32Codec.decode("MZXW6YTBOI"))
                .isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void roundTrip_randomBytes_returnsOriginal() {
        byte[] original = new byte[20];
        Arrays.fill(original, (byte) 0x5A);
        String encoded = Base32Codec.encode(original);
        assertThat(Base32Codec.decode(encoded)).isEqualTo(original);
    }
}
