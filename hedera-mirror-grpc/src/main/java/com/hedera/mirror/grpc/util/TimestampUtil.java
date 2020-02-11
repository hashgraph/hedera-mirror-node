package com.hedera.mirror.grpc.util;

import com.hederahashgraph.api.proto.java.Timestamp;
import io.grpc.Status;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TimestampUtil {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    public static final long LONG_MAX_SECONDS = Long.MAX_VALUE / NANOS_PER_SECOND * NANOS_PER_SECOND;
    public static final int LONG_MAX_NANOSECONDS = (int) (Long.MAX_VALUE % NANOS_PER_SECOND);

    public static boolean isValidTimeStamp(Timestamp endTimeStamp) {
        if (endTimeStamp == null) {
            return false;
        }

        if (endTimeStamp.getSeconds() < 0 || endTimeStamp.getNanos() < 0) {
            log.warn("Negative endTimeStamp supplied");
            throw Status.INVALID_ARGUMENT.augmentDescription("Negative endTimeStamp supplied").asRuntimeException();
        }

        // valid if seconds is less than max or if seconds is at max and nanoseconds are at or below max
        // 0 <= valid_time < LONG_MAX_SECONDS (9_223_372_036_000_000_000L) + LONG_MAX_NANOSECONDS (854_775_807)
        return (endTimeStamp.getSeconds() < LONG_MAX_SECONDS) ||
                (endTimeStamp.getSeconds() == LONG_MAX_SECONDS && endTimeStamp.getNanos() <= LONG_MAX_NANOSECONDS);
    }
}
