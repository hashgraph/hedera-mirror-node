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

import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProtoUtil {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    public static final long LONG_MAX_SECONDS = Long.MAX_VALUE / NANOS_PER_SECOND * NANOS_PER_SECOND;
    public static final int LONG_MAX_NANOSECONDS = (int) (Long.MAX_VALUE % NANOS_PER_SECOND);

    public static final Instant fromTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }

        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static final Timestamp toTimestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp
                .newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public static TimestampLongSupportRange getTimestampLongSupportRange(Timestamp timestamp) {
        if (timestamp == null) {
            return TimestampLongSupportRange.INVALID;
        }

        if (timestamp.getSeconds() < 0 || timestamp.getNanos() < 0) {
            return TimestampLongSupportRange.BELOW;
        }

        // valid if seconds is less than max or if seconds is at max and nanoseconds are at or below max
        // 0 <= valid_time < LONG_MAX_SECONDS (9_223_372_036_000_000_000L) + LONG_MAX_NANOSECONDS (854_775_807)
        if ((timestamp.getSeconds() < LONG_MAX_SECONDS) ||
                (timestamp.getSeconds() == LONG_MAX_SECONDS && timestamp.getNanos() <= LONG_MAX_NANOSECONDS)) {
            return TimestampLongSupportRange.WITHIN;
        }

        return TimestampLongSupportRange.ABOVE;
    }

    public enum TimestampLongSupportRange {
        BELOW,
        WITHIN,
        ABOVE,
        INVALID
    }
}
