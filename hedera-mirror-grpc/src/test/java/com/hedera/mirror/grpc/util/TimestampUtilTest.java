package com.hedera.mirror.grpc.util;

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.Timestamp;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TimestampUtilTest {

    @DisplayName("Check if Timestamp is within valid range")
    @ParameterizedTest(name = "Second(s) :{0} and nanosecond :{1}ns are in range : {2}")
    @CsvSource({
            "0, 0, true",
            "0, 999999999, true",
            "10, 0, true",
            "31556889864403199, 999999999, true",
            "8223372036000000000, 999999999, true",
            "9223372036000000000, 854775807, true",
            "9223372036000000000, 854775808, false",
            "9223372036000000001, 775807, false"
    })
    void isValidTimeStamp(long seconds, int nanos, boolean isValid) {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        assertEquals(isValid, TimestampUtil.isValidTimeStamp(timestamp));
    }

    @DisplayName("Negative timestamp provided")
    @ParameterizedTest(name = "with {0}s and {1}ns")
    @CsvSource({
            "-1, -1",
            "0, -1",
            "-1, 0"
    })
    void negativeTimestamp(long seconds, int nanos) {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        assertThrows(StatusRuntimeException.class, () -> TimestampUtil.isValidTimeStamp(timestamp));
    }

    @Test
    void isValidTimeStampLongThreshold() {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(TimestampUtil.LONG_MAX_SECONDS)
                .setNanos(TimestampUtil.LONG_MAX_NANOSECONDS).build();
        assertTrue(TimestampUtil.isValidTimeStamp(timestamp));
    }

    @Test
    void isValidTimeStampNull() {
        assertFalse(TimestampUtil.isValidTimeStamp(null));
    }

    @Test
    void isValidTimeStampMax() {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).setNanos(Integer.MAX_VALUE).build();
        assertFalse(TimestampUtil.isValidTimeStamp(timestamp));
    }
}
