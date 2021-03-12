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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.util.Utility;

class ProtoBalanceFileReaderTest {

    private static final String TIMESTAMP = "2021-03-08T20_15_00Z";
    private static final String FILEPATH = "data/accountBalances/proto/" + TIMESTAMP + "_Balances.pb.gz";

    private AccountBalanceFile expected;
    private ProtoBalanceFileReader protoBalanceFileReader;
    private StreamFileData streamFileData;

    @BeforeEach
    void setUp() {
        File file = TestUtils.getResource(FILEPATH).toPath().toFile();
        streamFileData = StreamFileData.from(file);
        expected = getExpectedAccountBalanceFile(streamFileData);

        protoBalanceFileReader = new ProtoBalanceFileReader();
    }

    @Test
    void readGzippedProtoBalanceFile() {
        assertThat(protoBalanceFileReader.read(streamFileData)).usingRecursiveComparison()
                .ignoringFields("loadStart", "loadEnd", "nodeAccountId")
                .isEqualTo(expected);
    }

    @Test
    void readCorruptedBytes() {
        corrupt(streamFileData.getBytes());

        assertThrows(InvalidStreamFileException.class, () -> protoBalanceFileReader.read(streamFileData));
    }

    private void corrupt(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (bytes[i] ^ 0xff);
        }
    }

    private AccountBalanceFile getExpectedAccountBalanceFile(StreamFileData streamFileData) {
        Instant instant = Instant.parse(TIMESTAMP.replace("_", ":"));
        long consensusTimestamp = Utility.convertToNanosMax(instant);

        long accountNum = 2000;
        long hbarBalance = 3000;
        long tokenNum = 5000;
        long tokenBalance = 6000;

        List<AccountBalance> accountBalances = IntStream.range(0, 10).mapToObj(i -> {
            EntityId accountId = EntityId.of(0, 0, accountNum + i, EntityTypeEnum.ACCOUNT);
            List<TokenBalance> tokenBalances = IntStream.range(0, 5).mapToObj(j -> {
                EntityId tokenId = EntityId.of(0, 0, tokenNum + i * 5 + j, EntityTypeEnum.TOKEN);
                return new TokenBalance(tokenBalance + i * 5 + j,
                        new TokenBalance.Id(consensusTimestamp, accountId, tokenId));
            })
                    .collect(Collectors.toList());
            return new AccountBalance(hbarBalance + i, tokenBalances, new AccountBalance.Id(consensusTimestamp, accountId));
        })
                .collect(Collectors.toList());
        return AccountBalanceFile.builder()
                .bytes(streamFileData.getBytes())
                .consensusTimestamp(consensusTimestamp)
                .count(10L)
                .fileHash("67c2fd054621366dd5a37b6ee36a51bc590361379d539fdac2265af08cb8097729218c7d9ff1f1e354c85b820c5b8cf8")
                .items(accountBalances)
                .name(streamFileData.getFilename())
                .build();
    }
}
