package com.hedera.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.aggregator.LogsBloomAggregator;

public class LogsBloomAggregatorTest {

    @Test
    void getLogsBloomWhenEmpty() {
        assertThat(new LogsBloomAggregator().getBloom()).isEqualTo(new byte[0]);
    }

    @Test
    void getLogsBloomInsertBytesTest() {
        byte[] bytes1 = { 127, -128, 78, -1, -19, -26, 125, 15, -14, -127, -75, 3, -62, -57, -35, 14, -69, -80, 43,
                113 };
        byte[] bytes2 = { -127, 1, 99, -54, -4, 126, -64, -78, -115, -70, -122, 127, 127, 54, -95, -40, -25, 84, 11,
                59 };
        byte[] bytes3 = { 127, 127, -17, 3, -55, -10, -13, 127, -50, -61, -97, 19, -9, -2, 38, -121, -104, 103, -34,
                -52 };

        LogsBloomAggregator bloomAggregator = new LogsBloomAggregator();
        bloomAggregator.insertBytes(bytes1);
        byte[] expectedResult = new byte[] { -1, -1, -17, -1, -3, -2, -1, -1, -1, -5, -65, 127, -1, -1, -1, -33, -1,
                -9, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0 };
        assertThat(bloomAggregator.getBloom()).isNotEqualTo(expectedResult);
        bloomAggregator.insertBytes(bytes2);
        assertThat(bloomAggregator.getBloom()).isNotEqualTo(expectedResult);
        bloomAggregator.insertBytes(bytes3);
        assertThat(bloomAggregator.getBloom()).isEqualTo(expectedResult);

        // Already inserted bytes should not change the filter
        bloomAggregator.insertBytes(bytes3);
        assertThat(bloomAggregator.getBloom()).isEqualTo(expectedResult);
    }
}
