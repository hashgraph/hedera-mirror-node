package com.hedera.mirror.importer.parser.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.google.common.base.Splitter;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

@Value
@RequiredArgsConstructor
public class AccountBalanceItem {
    private static final Splitter SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

    private final EntityId accountId;
    private final long balance;
    private final long consensusTimestamp;

    @Override
    public String toString() {
        Instant instant = Instant.ofEpochSecond(0, consensusTimestamp);
        return String.format("%s=%d,%s", accountId.entityIdToString(), balance, instant);
    }

    /**
     * Creates an AccountBalanceItem object from a line in the balances csv file and its consensus timestamp.
     * The account balance line is in the format of "shard,realm,account,balance".
     * @param line A line from the balances csv file
     * @param consensusTimestamp The consensus timestamp of the account balance line
     * @return A new <code>AccountBalanceItem</code> object
     * @exception IllegalArgumentException if the account balance line is malformed
     */
    public static AccountBalanceItem of(String line, long consensusTimestamp) {
        try {
            List<Long> parts = SPLITTER.splitToStream(line)
                    .map(Long::valueOf)
                    .filter(n -> n >= 0)
                    .collect(Collectors.toList());
            if (parts.size() != 4) {
                throw new IllegalArgumentException("Invalid account balance line: " + line);
            }

            EntityId accountId = EntityId.of(parts.get(0), parts.get(1), parts.get(2), EntityTypeEnum.ACCOUNT);
            return new AccountBalanceItem(accountId, parts.get(3), consensusTimestamp);
        } catch (NullPointerException | NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid account balance line: " + line, ex);
        }
    }
}
