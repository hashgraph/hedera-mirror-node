/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.ethereum.LegacyEthereumTransactionParserTest;
import com.hedera.mirror.importer.repository.EthereumTransactionRepository;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.FileID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

@RequiredArgsConstructor
class EntityRecordItemListenerEthereumTest extends AbstractEntityRecordItemListenerTest {
    private static final Version HAPI_VERSION_0_46_0 = new Version(0, 46, 0);

    private final EthereumTransactionRepository ethereumTransactionRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setEthereumTransactions(true);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void ethereumTransactionEip1559(boolean create) {
        RecordItem recordItem = recordItemBuilder.ethereumTransaction(create).build();
        var record = recordItem.getTransactionRecord();
        var functionResult = create ? record.getContractCreateResult() : record.getContractCallResult();
        var senderId = EntityId.of(functionResult.getSenderId());
        Entity sender = domainBuilder
                .entity()
                .customize(e -> e.id(senderId.getId()).num(senderId.getNum()))
                .persist();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractRepository.count()),
                () -> assertEquals(1, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem, sender, create));
    }

    @ParameterizedTest
    @MethodSource("provideSignatureNonceArguments")
    void ethereumTransactionSignatureNonce(boolean create, boolean setPriorHapiVersion) {
        var builder = recordItemBuilder.ethereumTransaction(create);
        if (setPriorHapiVersion) {
            builder.record(x -> {
                        // This version has no signer nonce so create it with a new builder to remove the field
                        var contractFunctionResult = ContractFunctionResult.newBuilder()
                                .setSenderId(recordItemBuilder.accountId())
                                .build();
                        if (create) {
                            x.setContractCreateResult(contractFunctionResult);
                        } else {
                            x.setContractCallResult(contractFunctionResult);
                        }
                    })
                    .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_46_0));
        }

        var recordItem = builder.build();
        var record = recordItem.getTransactionRecord();
        var functionResult = create ? record.getContractCreateResult() : record.getContractCallResult();
        var senderId = EntityId.of(functionResult.getSenderId());
        Entity sender = domainBuilder
                .entity()
                .customize(e -> e.id(senderId.getId()).num(senderId.getNum()))
                .persist();

        parseRecordItemAndCommit(recordItem);
        assertEthereumTransaction(recordItem, sender, create);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void ethereumTransactionNullSignatureNonce(boolean create) {
        var recordItem = recordItemBuilder
                .ethereumTransaction(create)
                .record(x -> x.setContractCallResult(
                        recordItemBuilder.contractFunctionResult().clearSignerNonce()))
                .build();

        var record = recordItem.getTransactionRecord();
        var functionResult = create ? record.getContractCreateResult() : record.getContractCallResult();
        var senderId = EntityId.of(functionResult.getSenderId());
        long nonceValue = 500L;
        Entity sender = domainBuilder
                .entity()
                .customize(e -> e.id(senderId.getId()).num(senderId.getNum()).ethereumNonce(nonceValue))
                .persist();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        var ethereumNonce = entityRepository.findById(sender.getId()).get().getEthereumNonce();
        // the nonce value is unchanged
        assertThat(ethereumNonce).isEqualTo(nonceValue);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void ethereumTransactionLegacy(boolean create) {
        RecordItem recordItem =
                getEthereumTransactionRecordItem(create, LegacyEthereumTransactionParserTest.LEGACY_RAW_TX);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem, null, create));
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void ethereumTransactionEip155(boolean create) {
        RecordItem recordItem =
                getEthereumTransactionRecordItem(create, LegacyEthereumTransactionParserTest.EIP155_RAW_TX);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem, null, create));
    }

    @Test
    void ethereumTransactionLegacyBadBytes() {
        var transactionBytes = RLPEncoder.list(Integers.toBytes(1), Integers.toBytes(2), Integers.toBytes(3));
        RecordItem recordItem = recordItemBuilder
                .ethereumTransaction(true)
                .transactionBody(x -> x.setEthereumData(ByteString.copyFrom(transactionBytes)))
                .build();

        assertDoesNotThrow(() -> parseRecordItemAndCommit(recordItem));
    }

    @SneakyThrows
    private RecordItem getEthereumTransactionRecordItem(boolean create, String transactionBytesString) {
        var transactionBytes = Hex.decodeHex(transactionBytesString);
        return recordItemBuilder
                .ethereumTransaction(create)
                .transactionBody(x -> x.setEthereumData(ByteString.copyFrom(transactionBytes)))
                .record(x -> x.setEthereumHash(ByteString.copyFrom(domainBuilder.bytes(32))))
                .build();
    }

    private void assertEthereumTransaction(RecordItem recordItem, Entity sender, boolean create) {
        long createdTimestamp = recordItem.getConsensusTimestamp();
        var ethTransaction =
                ethereumTransactionRepository.findById(createdTimestamp).get();
        var transactionBody = recordItem.getTransactionBody().getEthereumTransaction();
        var transactionRecord = recordItem.getTransactionRecord();

        var fileId = transactionBody.getCallData() == FileID.getDefaultInstance()
                ? null
                : EntityId.of(transactionBody.getCallData());
        assertThat(ethTransaction)
                .isNotNull()
                .returns(fileId, EthereumTransaction::getCallDataId)
                .returns(DomainUtils.toBytes(transactionBody.getEthereumData()), EthereumTransaction::getData)
                .returns(transactionBody.getMaxGasAllowance(), EthereumTransaction::getMaxGasAllowance)
                .returns(DomainUtils.toBytes(transactionRecord.getEthereumHash()), EthereumTransaction::getHash);

        if (sender != null) {
            var ethereumNonce = entityRepository.findById(sender.getId()).get().getEthereumNonce();
            if (recordItem.getHapiVersion().isLessThan(RecordFile.HAPI_VERSION_0_47_0)) {
                assertThat(ethereumNonce).isEqualTo(ethTransaction.getNonce() + 1);
            } else {
                var functionResult = create
                        ? transactionRecord.getContractCreateResult()
                        : transactionRecord.getContractCallResult();
                var expectedNonce = functionResult.getSignerNonce().getValue();
                assertThat(ethereumNonce).isEqualTo(expectedNonce);
            }
        }
    }

    private static Stream<Arguments> provideSignatureNonceArguments() {
        return Stream.of(
                Arguments.of(true, false),
                Arguments.of(true, true),
                Arguments.of(false, false),
                Arguments.of(false, true));
    }
}
