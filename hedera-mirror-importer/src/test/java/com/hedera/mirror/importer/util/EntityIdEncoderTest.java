package com.hedera.mirror.importer.util;

import static com.hedera.mirror.importer.util.EntityIdEncoder.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EntityIdEncoderTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, 0",
            "0, 0, 10, 10",
            "0, 0, 4294967295, 4294967295",
            "10, 10, 10, 2814792716779530",
            "32767, 65535, 4294967295, 9223372036854775807", // max +ve for shard, max for realm, max for num = max +ve long
            "32767, 0, 0, 9223090561878065152" // max -ve long
    })
    void testEntityEncoding(long shard, long realm, long num, long encodedId) {
        assertThat(EntityIdEncoder.encode(shard, realm, num)).isEqualTo(encodedId);
    }

    @Test
    void throwsExceptionForOutofBound() {
        assertThrows(IllegalArgumentException.class, () -> {
            EntityIdEncoder.encode(1L << SHARD_BITS, 0, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            EntityIdEncoder.encode(0, 1L << REALM_BITS, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            EntityIdEncoder.encode(0, 0, 1L << NUM_BITS);
        });
    }
}
