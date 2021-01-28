package com.hedera.mirror.importer.reader.balance.line;

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

import com.google.common.base.Splitter;
import java.util.Collections;
import java.util.List;
import javax.inject.Named;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

@Named
public class AccountBalanceLineParserV1 implements AccountBalanceLineParser {
    private static final Splitter SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

    /**
     * Parses an account balance line to extract shard, realm, account, and balance. If the shard matches
     * systemShardNum, creates and returns an {@code AccountBalance} entity object. The account balance line should be
     * in the format of "shard,realm,account,balance"
     *
     * @param line               The account balance line
     * @param consensusTimestamp The consensus timestamp of the account balance line
     * @param systemShardNum     The system shard number
     * @return {@code AccountBalance} entity object
     * @throws InvalidDatasetException if the line is malformed or the shard does not match {@code systemShardNum}
     */
    @Override
    public AccountBalance parse(String line, long consensusTimestamp, long systemShardNum) {
        try {
            if (line == null) {
                throw new InvalidDatasetException("Null line cannot be parsed");
            }
            List<String> parts = SPLITTER.splitToList(line);
            if (parts.size() != 4) {
                throw new InvalidDatasetException("Invalid account balance line: " + line);
            }

            long shardNum = Long.parseLong(parts.get(0));
            int realmNum = Integer.parseInt(parts.get(1));
            int accountNum = Integer.parseInt(parts.get(2));
            long balance = Long.parseLong(parts.get(3));
            if (shardNum < 0 || realmNum < 0 || accountNum < 0 || balance < 0) {
                throw new InvalidDatasetException("Invalid account balance line: " + line);
            }

            if (shardNum != systemShardNum) {
                throw new InvalidDatasetException(String.format("Invalid account balance line: %s. Expect " +
                        "shard (%d), got shard (%d)", line, systemShardNum, shardNum));
            }

            return new AccountBalance(balance, Collections
                    .emptyList(), new AccountBalance.Id(consensusTimestamp, EntityId
                    .of(shardNum, realmNum, accountNum, EntityTypeEnum.ACCOUNT)));
        } catch (NumberFormatException ex) {
            throw new InvalidDatasetException("Invalid account balance line: " + line, ex);
        }
    }
}
