/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.reader.balance;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hedera.services.stream.proto.AllAccountBalances;
import com.hedera.services.stream.proto.SingleAccountBalances;
import jakarta.inject.Named;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.CustomLog;
import org.apache.commons.codec.digest.DigestUtils;

@CustomLog
@Named
public class ProtoBalanceFileReader implements BalanceFileReader {

    private static final String FILE_EXTENSION = "pb";

    @Override
    public boolean supports(StreamFileData streamFileData) {
        return FILE_EXTENSION.equals(
                streamFileData.getStreamFilename().getExtension().getName());
    }

    @Override
    public AccountBalanceFile read(StreamFileData streamFileData) {
        Instant loadStart = Instant.now();

        try {
            var bytes = streamFileData.getDecompressedBytes();
            var allAccountBalances = AllAccountBalances.parseFrom(bytes);

            if (!allAccountBalances.hasConsensusTimestamp()) {
                throw new InvalidStreamFileException("Missing required consensusTimestamp field");
            }

            long consensusTimestamp = DomainUtils.timestampInNanosMax(allAccountBalances.getConsensusTimestamp());
            var items = allAccountBalances.getAllAccountsList().stream()
                    .map(ab -> toAccountBalance(consensusTimestamp, ab))
                    .toList();

            AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
            accountBalanceFile.setBytes(streamFileData.getBytes());
            accountBalanceFile.setConsensusTimestamp(consensusTimestamp);
            accountBalanceFile.setFileHash(DigestUtils.sha384Hex(bytes));
            accountBalanceFile.setItems(items);
            accountBalanceFile.setLoadStart(loadStart.getEpochSecond());
            accountBalanceFile.setName(streamFileData.getFilename());
            return accountBalanceFile;
        } catch (IOException e) {
            throw new StreamFileReaderException(e);
        }
    }

    private AccountBalance toAccountBalance(long consensusTimestamp, SingleAccountBalances balances) {
        EntityId accountId = EntityId.of(balances.getAccountID());
        List<TokenBalance> tokenBalances = balances.getTokenUnitBalancesList().stream()
                .map(tokenBalance -> {
                    EntityId tokenId = EntityId.of(tokenBalance.getTokenId());
                    TokenBalance.Id id = new TokenBalance.Id(consensusTimestamp, accountId, tokenId);
                    return new TokenBalance(tokenBalance.getBalance(), id);
                })
                .toList();
        return new AccountBalance(
                balances.getHbarBalance(), tokenBalances, new AccountBalance.Id(consensusTimestamp, accountId));
    }
}
