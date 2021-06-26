package com.hedera.mirror.importer.reader.balance;

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

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import reactor.core.publisher.Flux;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.services.stream.proto.SingleAccountBalances;

@Named
public class ProtoBalanceFileReader implements BalanceFileReader {

    private static final String FILE_EXTENSION = "pb";
    private static final int TAG_EOF = 0;
    private static final int TAG_TIMESTAMP = 10;
    private static final int TAG_BALANCE = 18;

    @Override
    public boolean supports(StreamFileData streamFileData) {
        return FILE_EXTENSION.equals(streamFileData.getStreamFilename().getExtension().getName());
    }

    @Override
    public AccountBalanceFile read(StreamFileData streamFileData) {
        Instant loadStart = Instant.now();
        Flux<AccountBalance> items = toFlux(streamFileData);
        long consensusTimestamp = items.map(AccountBalance::getId)
                .map(AccountBalance.Id::getConsensusTimestamp)
                .blockFirst();

        AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
        accountBalanceFile.setBytes(streamFileData.getBytes());
        accountBalanceFile.setConsensusTimestamp(consensusTimestamp);
        accountBalanceFile.setFileHash(getHash(streamFileData));
        accountBalanceFile.setItems(items);
        accountBalanceFile.setLoadStart(loadStart.getEpochSecond());
        accountBalanceFile.setName(streamFileData.getFilename());
        return accountBalanceFile;
    }

    private Flux<AccountBalance> toFlux(StreamFileData streamFileData) {
        return Flux.defer(() -> {
            InputStream inputStream = streamFileData.getInputStream();
            ExtensionRegistryLite extensionRegistry = ExtensionRegistryLite.getEmptyRegistry();
            CodedInputStream input = CodedInputStream.newInstance(inputStream);
            AtomicLong consensusTimestamp = new AtomicLong(0L);

            return Flux.<AccountBalance>generate(s -> {
                try {
                    while (true) {
                        switch (input.readTag()) {
                            case TAG_EOF:
                                s.complete();
                                return;
                            case TAG_TIMESTAMP:
                                Timestamp timestamp = input.readMessage(Timestamp.parser(), extensionRegistry);
                                consensusTimestamp.set(Utility.timestampInNanosMax(timestamp));
                                break;
                            case TAG_BALANCE:
                                var ab = input.readMessage(SingleAccountBalances.parser(), extensionRegistry);
                                s.next(toAccountBalance(consensusTimestamp.get(), ab));
                                return;
                        }
                    }
                } catch (java.io.IOException e) {
                    s.error(new StreamFileReaderException(e));
                }
            }).doFinally(s -> IOUtils.closeQuietly(inputStream));
        });
    }

    private AccountBalance toAccountBalance(long consensusTimestamp, SingleAccountBalances balances) {
        EntityId accountId = EntityId.of(balances.getAccountID());
        List<TokenBalance> tokenBalances = balances.getTokenUnitBalancesList().stream()
                .map(tokenBalance -> {
                    EntityId tokenId = EntityId.of(tokenBalance.getTokenId());
                    TokenBalance.Id id = new TokenBalance.Id(consensusTimestamp, accountId, tokenId);
                    return new TokenBalance(tokenBalance.getBalance(), id);
                })
                .collect(Collectors.toList());
        return new AccountBalance(
                balances.getHbarBalance(),
                tokenBalances,
                new AccountBalance.Id(consensusTimestamp, accountId)
        );
    }

    // Calculating the hash from the raw byte stream is 5x faster than parsing the protobuf
    private String getHash(StreamFileData streamFileData) {
        byte[] buffer = new byte[8192];
        MessageDigest messageDigest = DigestUtils.getSha384Digest();

        try (InputStream inputStream = new DigestInputStream(streamFileData.getInputStream(), messageDigest)) {
            while (inputStream.read(buffer) >= 0) {
            }
        } catch (Exception e) {
            throw new StreamFileReaderException(e);
        }

        return Utility.bytesToHex(messageDigest.digest());
    }
}
