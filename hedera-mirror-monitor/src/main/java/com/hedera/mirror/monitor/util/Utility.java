package com.hedera.mirror.monitor;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

@Log4j2
@UtilityClass
public class Utility {

    private static final long MILLIS_OFFSET = Duration.ofMinutes(5L).toMillis();
    private static final Random RANDOM = new SecureRandom();

    /**
     * Parses bytes as a String expected to be in format ^\d+ .*$. The first part is the published timestamp in
     * milliseconds from epoch followed by a mandatory space. Optionally, additional arbitrary characters can be
     * appended that are ignored by this method.
     *
     * @param bytes containing a timestamp encoded as a String
     * @return the parsed Instant
     */
    public static Instant getTimestamp(byte[] bytes) {
        try {
            if (bytes == null) {
                return null;
            }

            String message = new String(bytes, StandardCharsets.US_ASCII);
            String[] parts = StringUtils.split(message, ' ');
            if (parts == null || parts.length <= 1) {
                return null;
            }

            long now = System.currentTimeMillis();
            Long timestamp = Long.parseLong(parts[0]);

            // Discard unreasonable values
            if (timestamp == null || timestamp < (now - MILLIS_OFFSET) || timestamp > (now + MILLIS_OFFSET)) {
                return null;
            }

            return Instant.ofEpochMilli(timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] generateMessage(int requestedMessageSize) {
        String message = System.currentTimeMillis() + " ";

        if (message.length() < requestedMessageSize) {
            int length = requestedMessageSize - message.length();
            message += RandomStringUtils.random(length, 0, 0, true, false, null, RANDOM);
        }

        return message.getBytes(StandardCharsets.US_ASCII);
    }

    public static String getMemo(String message) {
        return System.currentTimeMillis() + " " + message;
    }
}
