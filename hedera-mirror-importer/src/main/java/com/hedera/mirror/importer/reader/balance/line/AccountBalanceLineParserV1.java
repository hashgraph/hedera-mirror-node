/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.reader.balance.line;

import com.google.common.base.Splitter;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class AccountBalanceLineParserV1 implements AccountBalanceLineParser {

    private static final Splitter SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

    private final MirrorProperties mirrorProperties;

    /**
     * Parses an account balance line to extract shard, realm, account, and balance. If the shard matches
     * systemShardNum, creates and returns an {@code AccountBalance} entity object. The account balance line should be
     * in the format of "shard,realm,account,balance"
     *
     * @param line               The account balance line
     * @param consensusTimestamp The consensus timestamp of the account balance line
     * @return {@code AccountBalance} entity object
     * @throws InvalidDatasetException if the line is malformed or the shard does not match {@code systemShardNum}
     */
    @Override
    public AccountBalance parse(String line, long consensusTimestamp) {
        try {
            if (line == null) {
                throw new InvalidDatasetException("Null line cannot be parsed");
            }
            List<String> parts = SPLITTER.splitToList(line);
            if (parts.size() != 4) {
                throw new InvalidDatasetException(INVALID_BALANCE + line);
            }

            long shardNum = Long.parseLong(parts.get(0));
            int realmNum = Integer.parseInt(parts.get(1));
            int accountNum = Integer.parseInt(parts.get(2));
            long balance = Long.parseLong(parts.get(3));
            if (shardNum < 0 || realmNum < 0 || accountNum < 0 || balance < 0) {
                throw new InvalidDatasetException(INVALID_BALANCE + line);
            }

            if (shardNum != mirrorProperties.getShard()) {
                throw new InvalidDatasetException(String.format(
                        "Invalid account balance line: %s. Expect " + "shard (%d), got shard (%d)",
                        line, mirrorProperties.getShard(), shardNum));
            }

            return new AccountBalance(
                    balance,
                    Collections.emptyList(),
                    new AccountBalance.Id(
                            consensusTimestamp, EntityId.of(shardNum, realmNum, accountNum, EntityType.ACCOUNT)));
        } catch (NumberFormatException ex) {
            throw new InvalidDatasetException(INVALID_BALANCE + line, ex);
        }
    }
}
