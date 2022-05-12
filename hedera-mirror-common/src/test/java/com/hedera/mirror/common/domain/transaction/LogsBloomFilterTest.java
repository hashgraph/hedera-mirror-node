package com.hedera.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class LogsBloomFilterTest {

    @Test
    void getLogsBloomWhenEmpty() {
        assertThat(new LogsBloomFilter().getBloom()).isEqualTo(new byte[0]);
    }
    
    @Test
    void getLogsBloomInsertBytesTest() {
        byte[] bytes1 = {-105, 32, 78, -1, -19, -26, 125, 15, -14, 80, -75, 3, -62, -57, -35, 14, -69, -80, 43, 113};
        byte[] bytes2 = {52, 33, 99, -54, -4, 126, -64, -78, -115, -70, -122, 43, 127, 54, -95, -40, -25, 84, 11, 59};
        byte[] bytes3 = {-38, -27, -17, 3, -55, -10, -13, 29, -50, -61, -97, 19, -9, -2, 38, -121, -104, 103, -34, -52};

        LogsBloomFilter bloomFilter = new LogsBloomFilter();
        bloomFilter.insertBytes(bytes1);
        byte[] expectedResult = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 4, 0, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0};
        assertThat(bloomFilter.getBloom()).isNotEqualTo(expectedResult);
        bloomFilter.insertBytes(bytes2);
        assertThat(bloomFilter.getBloom()).isNotEqualTo(expectedResult);
        bloomFilter.insertBytes(bytes3);
        assertThat(bloomFilter.getBloom()).isEqualTo(expectedResult);

        // Already inserted bytes should not change the filter
        bloomFilter.insertBytes(bytes3);
        assertThat(bloomFilter.getBloom()).isEqualTo(expectedResult);
    }
}
