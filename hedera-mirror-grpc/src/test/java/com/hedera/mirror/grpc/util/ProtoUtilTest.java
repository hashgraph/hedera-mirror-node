package com.hedera.mirror.grpc.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ProtoUtilTest {

    @DisplayName("Convert Timestamp to Instant")
    @ParameterizedTest(name = "with {0}s and {1}ns")
    @CsvSource({
            "0, 0",
            "0, 999999999",
            "10, 0",
            "31556889864403199, 999999999",
            "-31557014167219200, 0"
    })
    void fromTimestamp(long seconds, int nanos) {
        Instant instant = Instant.ofEpochSecond(seconds, nanos);
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        assertThat(ProtoUtil.fromTimestamp(timestamp)).isEqualTo(instant);
    }

    @DisplayName("Convert Instant to Timestamp")
    @ParameterizedTest(name = "with {0}s and {1}ns")
    @CsvSource({
            "0, 0",
            "0, 999999999",
            "10, 0",
            "31556889864403199, 999999999",
            "-31557014167219200, 0"
    })
    void toTimestamp(long seconds, int nanos) {
        Instant instant = Instant.ofEpochSecond(seconds, nanos);
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        assertThat(ProtoUtil.toTimestamp(instant)).isEqualTo(timestamp);
    }

    @DisplayName("Check if Timestamp is within valid range")
    @ParameterizedTest(name = "Second(s) :{0} and nanosecond :{1}ns are {2} range")
    @CsvSource({
            "-1, -1, BELOW",
            "0, -1, BELOW",
            "-1, 0, BELOW",
            "0, 0, WITHIN",
            "0, 999999999, WITHIN",
            "10, 0, WITHIN",
            "31556889864403199, 999999999, WITHIN",
            "8223372036000000000, 999999999, WITHIN",
            "9223372036000000000, 854775807, WITHIN",
            "9223372036000000000, 854775808, ABOVE",
            "9223372036000000001, 775807, ABOVE"
    })
    void isValidTimeStamp(long seconds, int nanos, String timestampLongSupportRange) {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        ProtoUtil.TimestampLongSupportRange supportRange = ProtoUtil.TimestampLongSupportRange
                .valueOf(timestampLongSupportRange);
        assertEquals(supportRange, ProtoUtil.getTimestampLongSupportRange(timestamp));
    }

    @Test
    void isValidTimeStampLongThreshold() {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(ProtoUtil.LONG_MAX_SECONDS)
                .setNanos(ProtoUtil.LONG_MAX_NANOSECONDS).build();
        assertEquals(ProtoUtil.TimestampLongSupportRange.WITHIN, ProtoUtil.getTimestampLongSupportRange(timestamp));
    }

    @Test
    void isValidTimeStampNull() {
        assertEquals(ProtoUtil.TimestampLongSupportRange.INVALID, ProtoUtil.getTimestampLongSupportRange(null));
    }

    @Test
    void isValidTimeStampMin() {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(Long.MIN_VALUE).setNanos(Integer.MIN_VALUE).build();
        assertEquals(ProtoUtil.TimestampLongSupportRange.BELOW, ProtoUtil.getTimestampLongSupportRange(timestamp));
    }

    @Test
    void isValidTimeStampMax() {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).setNanos(Integer.MAX_VALUE).build();
        assertEquals(ProtoUtil.TimestampLongSupportRange.ABOVE, ProtoUtil.getTimestampLongSupportRange(timestamp));
    }
}
