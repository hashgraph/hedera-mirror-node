package com.hedera.mirror.importer.parser.record.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.FileID;
import javax.annotation.Resource;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.record.ethereum.LegacyEthereumTransactionParserTest;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.EthereumTransactionRepository;

class EntityRecordItemListenerEthereumTest extends AbstractEntityRecordItemListenerTest {
    static final String LONDON_RAW_TX =
            "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";

    @Resource
    private ContractRepository contractRepository;

    @Resource
    private EthereumTransactionRepository ethereumTransactionRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setEthereumTransactions(true);
    }

    @Test
    void ethereumTransactionCallLondon() {
        RecordItem recordItem = recordItemBuilder.ethereumTransaction(false)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem)
        );
    }

    @Test
    void ethereumTransactionCallLegacy() {
        RecordItem recordItem = getEthereumTransactionRecordItem(false,
                LegacyEthereumTransactionParserTest.LEGACY_RAW_TX);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem)
        );
    }

    @Test
    void ethereumTransactionCallEIP155() {
        RecordItem recordItem = getEthereumTransactionRecordItem(false,
                LegacyEthereumTransactionParserTest.EIP155_RAW_TX);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem)
        );
    }

    @Test
    void ethereumEip1559TransactionCreate() {
        RecordItem recordItem = getEthereumTransactionRecordItem(true,
                LONDON_RAW_TX);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem)
        );
    }

    @Test
    void ethereumLegacyTransactionCreate() {
        RecordItem recordItem = getEthereumTransactionRecordItem(true,
                LegacyEthereumTransactionParserTest.LEGACY_RAW_TX);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem)
        );
    }

    @Test
    void ethereumLegacyChainListTransactionCreate() {
        RecordItem recordItem = getEthereumTransactionRecordItem(true,
                LegacyEthereumTransactionParserTest.EIP155_RAW_TX);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem)
        );
    }

    @Test
    void ethereumTransactionLegacyBadBytes() {
        var transactionBytes = RLPEncoder.encodeAsList(
                Integers.toBytes(1),
                Integers.toBytes(2),
                Integers.toBytes(3));
        RecordItem recordItem = recordItemBuilder.ethereumTransaction(true)
                .transactionBody(x -> x.setEthereumData(ByteString.copyFrom(transactionBytes)))
                .build();

        assertThrows(InvalidDatasetException.class, () -> parseRecordItemAndCommit(recordItem));
    }

    @SneakyThrows
    private RecordItem getEthereumTransactionRecordItem(boolean create, String transactionBytesString) {
        var transactionBytes = Hex.decodeHex(transactionBytesString);
        return recordItemBuilder.ethereumTransaction(create)
                .transactionBody(x -> x.setEthereumData(ByteString.copyFrom(transactionBytes)))
                .record(x -> x.setEthereumHash(ByteString.copyFrom(Hash.sha3(transactionBytes))))
                .build();
    }

    private void assertEthereumTransaction(RecordItem recordItem) {
        long createdTimestamp = recordItem.getConsensusTimestamp();
        var ethTransaction = ethereumTransactionRepository.findById(createdTimestamp).get();
        var transactionBody = recordItem.getTransactionBody().getEthereumTransaction();

        var fileId = transactionBody.getCallData() == FileID.getDefaultInstance() ? null :
                EntityId.of(transactionBody.getCallData());
        assertThat(ethTransaction)
                .isNotNull()
                .returns(fileId, EthereumTransaction::getCallDataId)
                .returns(DomainUtils.toBytes(transactionBody.getEthereumData()), EthereumTransaction::getData)
                .returns(transactionBody.getMaxGasAllowance(), EthereumTransaction::getMaxGasAllowance)
                .returns(DomainUtils.toBytes(recordItem.getRecord().getEthereumHash()), EthereumTransaction::getHash);
    }
}
