/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.ethereum;

import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.RAW_TX_TYPE_1;
import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.RAW_TX_TYPE_1_CALL_DATA;
import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.RAW_TX_TYPE_1_CALL_DATA_OFFLOADED;
import static com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionIntegrationTestUtility.loadEthereumTransactions;
import static com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionIntegrationTestUtility.populateFileData;
import static org.assertj.core.api.Assertions.assertThat;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@RequiredArgsConstructor
class EthereumTransactionHashServiceTest extends ImporterIntegrationTest {

    private final EthereumTransactionHashService service;

    @MethodSource("provideAllEthereumTransactions")
    @ParameterizedTest(name = "{0}")
    void getHash(String description, EthereumTransaction ethereumTransaction) {
        populateFileData(jdbcOperations);
        byte[] hash = service.getHash(
                ethereumTransaction.getCallDataId(),
                ethereumTransaction.getConsensusTimestamp(),
                ethereumTransaction.getData());
        assertThat(hash).isEqualTo(ethereumTransaction.getHash());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getHashType2CallDataFileData(boolean addHexPrefix) {
        // given
        byte[] expected = new Keccak.Digest256().digest(RAW_TX_TYPE_1);
        var fileData = domainBuilder
                .fileData()
                .customize(f -> {
                    String data = addHexPrefix ? "0x" + RAW_TX_TYPE_1_CALL_DATA : RAW_TX_TYPE_1_CALL_DATA;
                    f.fileData(data.getBytes(StandardCharsets.UTF_8));
                })
                .persist();

        // when
        var actual = service.getHash(
                fileData.getEntityId(), fileData.getConsensusTimestamp() + 1, RAW_TX_TYPE_1_CALL_DATA_OFFLOADED);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void cannotReencodeWithAccessList(CapturedOutput capturedOutput) {
        // given
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).get();
        String expectedMessage = "Re-encoding ethereum transaction at %d with access list is unsupported"
                .formatted(ethereumTransaction.getConsensusTimestamp());

        // when, then
        softly.assertThat(service.getHash(ethereumTransaction)).isEmpty();
        softly.assertThat(capturedOutput.getAll()).contains(expectedMessage);
    }

    @Test
    void malformedCallDataFileData(CapturedOutput capturedOutput) {
        // given
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(false)
                .customize(t -> t.accessList(null))
                .get();
        domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(ethereumTransaction.getConsensusTimestamp() - 1)
                        .entityId(ethereumTransaction.getCallDataId())
                        .fileData(new byte[] {(byte) 0xff}))
                .persist();
        String expectedMessage = "Failed to decode / encode ethereum transaction at %d"
                .formatted(ethereumTransaction.getConsensusTimestamp());

        // when, then
        softly.assertThat(service.getHash(ethereumTransaction)).isEmpty();
        softly.assertThat(capturedOutput.getAll()).contains(expectedMessage);
    }

    @Test
    void missingCallDataFileData(CapturedOutput capturedOutput) {
        // given
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(false)
                .customize(t -> t.accessList(null))
                .get();
        String expectedMessage = "Call data not found from %s for ethereum transaction at %s"
                .formatted(ethereumTransaction.getCallDataId(), ethereumTransaction.getConsensusTimestamp());

        // when, then
        softly.assertThat(service.getHash(ethereumTransaction)).isEmpty();
        softly.assertThat(capturedOutput.getAll()).contains(expectedMessage);
    }

    @Test
    void unsupportedEthereumTransactionType(CapturedOutput capturedOutput) {
        // given
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(false)
                .customize(t -> t.accessList(null).type(0x10))
                .get();
        domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(ethereumTransaction.getConsensusTimestamp() - 1)
                        .entityId(ethereumTransaction.getCallDataId())
                        .fileData("a0".getBytes(StandardCharsets.UTF_8)))
                .persist();
        String expectedMessage = "Unsupported transaction type %d of ethereum transaction at %d"
                .formatted(ethereumTransaction.getType(), ethereumTransaction.getConsensusTimestamp());

        // when
        softly.assertThat(service.getHash(ethereumTransaction)).isEmpty();
        softly.assertThat(capturedOutput.getAll()).contains(expectedMessage);
    }

    @Test
    void unsupportedEthereumTransactionTypeFromRawBytes(CapturedOutput capturedOutput) {
        // given
        byte[] rawBytes = RLPEncoder.sequence(new byte[] {0x10}, List.of(new byte[] {0x01}, new byte[] {0x02}));
        long timestamp = domainBuilder.timestamp();
        String expectedMessage = "Failed to decode / encode ethereum transaction at %d".formatted(timestamp);

        // when
        softly.assertThat(service.getHash(domainBuilder.entityId(), timestamp, rawBytes))
                .isEmpty();
        softly.assertThat(capturedOutput.getAll()).contains(expectedMessage);
    }

    private static Stream<Arguments> provideAllEthereumTransactions() {
        return loadEthereumTransactions().stream()
                .map(ethereumTransaction -> Arguments.of(
                        String.format("Ethereum transaction at %d", ethereumTransaction.getConsensusTimestamp()),
                        ethereumTransaction));
    }
}
