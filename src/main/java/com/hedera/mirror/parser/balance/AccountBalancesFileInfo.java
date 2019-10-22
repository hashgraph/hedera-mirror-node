package com.hedera.mirror.parser.balance;

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

import com.hedera.mirror.util.TimestampConverter;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public final class AccountBalancesFileInfo {
    public static final Pattern FILENAME_PATTERN = Pattern.compile(
            "^(?<year>[0-9]{4})-(?<month>[0-9]{1,2})-(?<day>[0-9]{1,2})T(?<hour>[0-9]{1,2})_(?<minute>[0-9]{1,2})_(?<second>[0-9]{2})(\\.(?<subsecond>[0-9]{1,9}))?.*_balances\\.csv$",
            Pattern.CASE_INSENSITIVE);

    @Getter
    private final Instant filenameTimestamp;
    private final TimestampConverter timestampConverter = new TimestampConverter();

    public static boolean hasExpectedFilenameFormat(final Path filename) {
        return FILENAME_PATTERN.matcher(filename.getFileName().toString()).find();
    }

    /**
     * Given a path to an account balances file - validate that the filename matches the expected pattern and extract
     * the timestamp from the filename.
     * @param filePath
     * @throws IllegalArgumentException if the filename doesn't match the expected pattern
     */
    public AccountBalancesFileInfo(final Path filePath) throws IllegalArgumentException {
        final var fn = filePath.getFileName().toString();
        final Matcher m = FILENAME_PATTERN.matcher(fn);
        if (!m.find()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid date in account balance filename %s", fn));
        }
        try {
            filenameTimestamp = timestampConverter.toInstant(m);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format(
                    "Invalid date in account balance filename %s", fn), e);
        }
    }
}
