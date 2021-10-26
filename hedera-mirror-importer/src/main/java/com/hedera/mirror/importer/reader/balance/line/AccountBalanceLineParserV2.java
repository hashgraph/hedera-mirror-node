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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.importer.domain.EntityType;
import com.hederahashgraph.api.proto.java.TokenBalances;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Base64;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

@Named
@RequiredArgsConstructor
public class AccountBalanceLineParserV2 implements AccountBalanceLineParser {

    private static final Splitter SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

    private final MirrorProperties mirrorProperties;

    /**
     * Parses an account balance line to extract shard, realm, account, balance, and token balances. If the shard
     * matches systemShardNum, creates and returns an {@code AccountBalance} entity object. The account balance line
     * should be in the format of "shard,realm,account,balance"
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
            boolean hasTokenBalance;
            if (parts.size() == 5) {
                hasTokenBalance = true;
            } else if (parts.size() == 4) {
                hasTokenBalance = false;
            } else {
                throw new InvalidDatasetException("Invalid account balance line: " + line);
            }

            long shardNum = Long.parseLong(parts.get(0));
            int realmNum = Integer.parseInt(parts.get(1));
            int accountNum = Integer.parseInt(parts.get(2));
            long balance = Long.parseLong(parts.get(3));

            if (shardNum < 0 || realmNum < 0 || accountNum < 0 || balance < 0) {
                throw new InvalidDatasetException("Invalid account balance line: " + line);
            }

            if (shardNum != mirrorProperties.getShard()) {
                throw new InvalidDatasetException(String.format("Invalid account balance line: %s. Expect " +
                        "shard (%d), got shard (%d)", line, mirrorProperties.getShard(), shardNum));
            }

            EntityId accountId = EntityId
                    .of(shardNum, realmNum, accountNum, EntityType.ACCOUNT);

            List<TokenBalance> tokenBalances = hasTokenBalance ? parseTokenBalanceList(parts.get(4), consensusTimestamp,
                    accountId) : Collections
                    .emptyList();

            return new AccountBalance(balance, tokenBalances, new AccountBalance.Id(consensusTimestamp, accountId));
        } catch (NumberFormatException | InvalidProtocolBufferException ex) {
            throw new InvalidDatasetException("Invalid account balance line: " + line, ex);
        }
    }

    private List<TokenBalance> parseTokenBalanceList(String tokenBalancesProtoString, long consensusTimestamp,
                                                     EntityId accountId) throws InvalidProtocolBufferException {
        List<com.hederahashgraph.api.proto.java.TokenBalance> tokenBalanceProtoList =
                TokenBalances.parseFrom(Base64.decodeBase64(tokenBalancesProtoString)).getTokenBalancesList();
        List<TokenBalance> tokenBalances = new ArrayList<>();
        for (com.hederahashgraph.api.proto.java.TokenBalance tokenBalanceProto : tokenBalanceProtoList) {
            TokenID tokenId = tokenBalanceProto.getTokenId();
            TokenBalance tokenBalance = new TokenBalance(tokenBalanceProto
                    .getBalance(), new TokenBalance.Id(consensusTimestamp, accountId,
                    EntityId.of(tokenId)));
            tokenBalances.add(tokenBalance);
        }
        return tokenBalances;
    }
}
