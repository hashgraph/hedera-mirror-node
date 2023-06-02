/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.UnknownFieldSet;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hedera.services.stream.proto.SingleAccountBalances;
import com.hederahashgraph.api.proto.java.Timestamp;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

@Log4j2
@Named
public class ProtoBalanceFileReader implements BalanceFileReader {

    private static final String FILE_EXTENSION = "pb";
    private static final int TAG_EOF = 0;
    private static final int TAG_TIMESTAMP = 10;
    private static final int TAG_BALANCE = 18;

    @Override
    public boolean supports(StreamFileData streamFileData) {
        return FILE_EXTENSION.equals(
                streamFileData.getStreamFilename().getExtension().getName());
    }

    @Override
    public AccountBalanceFile read(StreamFileData streamFileData) {
        Instant loadStart = Instant.now();
        Flux<AccountBalance> items = toFlux(streamFileData);
        long consensusTimestamp = items.map(AccountBalance::getId)
                .map(AccountBalance.Id::getConsensusTimestamp)
                .blockFirst();

        try (InputStream inputStream = streamFileData.getInputStream()) {
            AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
            accountBalanceFile.setBytes(streamFileData.getBytes());
            accountBalanceFile.setConsensusTimestamp(consensusTimestamp);
            accountBalanceFile.setFileHash(DigestUtils.sha384Hex(inputStream));
            accountBalanceFile.setItems(items);
            accountBalanceFile.setLoadStart(loadStart.getEpochSecond());
            accountBalanceFile.setName(streamFileData.getFilename());
            return accountBalanceFile;
        } catch (IOException e) {
            throw new StreamFileReaderException(e);
        }
    }

    private Flux<AccountBalance> toFlux(StreamFileData streamFileData) {
        return Flux.defer(() -> {
            InputStream inputStream = streamFileData.getInputStream();
            ExtensionRegistryLite extensionRegistry = ExtensionRegistryLite.getEmptyRegistry();
            CodedInputStream input = CodedInputStream.newInstance(inputStream);
            AtomicLong consensusTimestamp = new AtomicLong(0L);
            UnknownFieldSet.Builder unknownFieldSet = UnknownFieldSet.newBuilder();

            return Flux.<AccountBalance>generate(sink -> {
                        try {
                            boolean done = false;
                            while (!done) {
                                int tag = input.readTag();
                                switch (tag) {
                                    case TAG_EOF:
                                        done = true;
                                        break;
                                    case TAG_TIMESTAMP:
                                        Timestamp timestamp = input.readMessage(Timestamp.parser(), extensionRegistry);
                                        consensusTimestamp.set(DomainUtils.timestampInNanosMax(timestamp));
                                        break;
                                    case TAG_BALANCE:
                                        Assert.state(consensusTimestamp.get() > 0, "Missing consensus timestamp)");
                                        var ab = input.readMessage(SingleAccountBalances.parser(), extensionRegistry);
                                        sink.next(toAccountBalance(consensusTimestamp.get(), ab));
                                        return;
                                    default:
                                        log.warn("Unsupported tag: {}", tag);
                                        done = !unknownFieldSet.mergeFieldFrom(tag, input);
                                }
                            }

                            Assert.state(consensusTimestamp.get() > 0, "Missing consensus timestamp)");
                            sink.complete();
                        } catch (IOException e) {
                            sink.error(new StreamFileReaderException(e));
                        } catch (IllegalStateException e) {
                            sink.error(new InvalidStreamFileException(e));
                        }
                    })
                    .doFinally(s -> IOUtils.closeQuietly(inputStream));
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
                .toList();
        return new AccountBalance(
                balances.getHbarBalance(), tokenBalances, new AccountBalance.Id(consensusTimestamp, accountId));
    }
}
