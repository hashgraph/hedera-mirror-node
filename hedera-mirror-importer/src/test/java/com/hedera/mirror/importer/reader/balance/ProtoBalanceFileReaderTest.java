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

import com.google.protobuf.UnknownFieldSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityType;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.services.stream.proto.AllAccountBalances;
import com.hedera.services.stream.proto.SingleAccountBalances;

class ProtoBalanceFileReaderTest {

    private static final String TIMESTAMP = "2021-03-08T20_15_00Z";
    private static final String FILEPATH = Paths.get("data", "accountBalances", "proto",
            TIMESTAMP + "_Balances.pb.gz").toString();

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
        AccountBalanceFile actual = protoBalanceFileReader.read(streamFileData);
        assertThat(actual).usingRecursiveComparison()
                .ignoringFields("loadStart", "nodeAccountId", "items")
                .isEqualTo(expected);
        assertThat(expected.getItems().collectList().block()).isEqualTo(actual.getItems().collectList().block());
        assertThat(actual.getLoadStart()).isNotNull().isPositive();
    }

    @Test
    void emptyProtobuf() {
        AllAccountBalances allAccountBalances = AllAccountBalances.newBuilder().build();
        byte[] bytes = allAccountBalances.toByteArray();
        StreamFileData streamFileData = StreamFileData.from(TIMESTAMP + "_Balances.pb", bytes);
        assertThrows(InvalidStreamFileException.class, () -> protoBalanceFileReader.read(streamFileData));
    }

    @Test
    void missingTimestamp() {
        AllAccountBalances allAccountBalances = AllAccountBalances.newBuilder()
                .addAllAccounts(SingleAccountBalances.newBuilder().build()).build();
        byte[] bytes = allAccountBalances.toByteArray();
        StreamFileData streamFileData = StreamFileData.from(TIMESTAMP + "_Balances.pb", bytes);
        assertThrows(InvalidStreamFileException.class, () -> protoBalanceFileReader.read(streamFileData));
    }

    @Test
    void unknownFields() {
        UnknownFieldSet.Field field = UnknownFieldSet.Field.newBuilder().addFixed32(11).build();
        AllAccountBalances allAccountBalances = AllAccountBalances.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1L).build())
                .mergeUnknownFields(UnknownFieldSet.newBuilder().addField(23, field).build())
                .addAllAccounts(SingleAccountBalances.newBuilder().build())
                .build();
        byte[] bytes = allAccountBalances.toByteArray();
        StreamFileData streamFileData = StreamFileData.from(TIMESTAMP + "_Balances.pb", bytes);
        AccountBalanceFile accountBalanceFile = protoBalanceFileReader.read(streamFileData);
        assertThat(accountBalanceFile).isNotNull();
        assertThat(accountBalanceFile.getItems().count().block()).isEqualTo(1L);
    }

    @Test
    void readCorruptedBytes() {
        corrupt(streamFileData.getBytes());

        assertThrows(InvalidStreamFileException.class, () -> protoBalanceFileReader.read(streamFileData));
    }

    @ParameterizedTest(name = "supports {0}")
    @ValueSource(strings = {"2021-03-10T16:00:00Z_Balances.pb.gz", "2021-03-10T16:00:00Z_Balances.pb"})
    void supports(String filename) {
        StreamFileData streamFileData = StreamFileData.from(filename, new byte[] {1, 2, 3});
        assertThat(protoBalanceFileReader.supports(streamFileData)).isTrue();
    }

    @ParameterizedTest(name = "does not support {0}")
    @ValueSource(strings = {"2021-03-10T16:00:00Z_Balances.csv", "2021-03-10T16:00:00Z_Balances.csv.gz"})
    void unsupported(String filename) {
        StreamFileData streamFileData = StreamFileData.from(filename, new byte[] {1, 2, 3});
        assertThat(protoBalanceFileReader.supports(streamFileData)).isFalse();
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
            EntityId accountId = EntityId.of(0, 0, accountNum + i, EntityType.ACCOUNT);
            List<TokenBalance> tokenBalances = IntStream.range(0, 5).mapToObj(j -> {
                EntityId tokenId = EntityId.of(0, 0, tokenNum + i * 5 + j, EntityType.TOKEN);
                return new TokenBalance(tokenBalance + i * 5 + j,
                        new TokenBalance.Id(consensusTimestamp, accountId, tokenId));
            })
                    .collect(Collectors.toList());
            return new AccountBalance(hbarBalance + i, tokenBalances, new AccountBalance.Id(consensusTimestamp,
                    accountId));
        })
                .collect(Collectors.toList());
        return AccountBalanceFile.builder()
                .bytes(streamFileData.getBytes())
                .consensusTimestamp(consensusTimestamp)
                .fileHash(
                        "67c2fd054621366dd5a37b6ee36a51bc590361379d539fdac2265af08cb8097729218c7d9ff1f1e354c85b820c5b8cf8")
                .items(Flux.fromIterable(accountBalances))
                .name(streamFileData.getFilename())
                .build();
    }
}
