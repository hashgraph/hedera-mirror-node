package com.hedera.mirror.importer.parser.balance;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.TokenBalances;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Named;
import org.apache.commons.codec.binary.Base64;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

@Named
public class AccountBalanceLineParserV2 {

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
    public AccountBalance parse(String line, long consensusTimestamp, long systemShardNum) {
        try {
            String[] parts = line.split(",");
            boolean hasTokenBalance;
            if (parts.length == 5) {
                hasTokenBalance = true;
            } else if (parts.length == 4) {
                hasTokenBalance = false;
            } else {
                throw new InvalidDatasetException("Invalid account balance line: " + line);
            }

            long shardNum = Long.parseLong(parts[0]);
            int realmNum = Integer.parseInt(parts[1]);
            int accountNum = Integer.parseInt(parts[2]);
            long balance = Long.parseLong(parts[3]);

            if (shardNum < 0 || realmNum < 0 || accountNum < 0 || balance < 0) {
                throw new InvalidDatasetException("Invalid account balance line: " + line);
            }

            if (shardNum != systemShardNum) {
                throw new InvalidDatasetException(String.format("Invalid account balance line: %s. Expect " +
                        "shard (%d), got shard (%d)", line, systemShardNum, shardNum));
            }

            EntityId accountId = EntityId
                    .of(shardNum, realmNum, accountNum, EntityTypeEnum.ACCOUNT);

            List<TokenBalance> tokenBalances = hasTokenBalance ? parseTokenBalanceList(parts[4], consensusTimestamp,
                    accountId) : Collections
                    .emptyList();

            return new AccountBalance(balance, tokenBalances, new AccountBalance.Id(consensusTimestamp, accountId));
        } catch (NullPointerException | NumberFormatException | InvalidProtocolBufferException ex) {
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
