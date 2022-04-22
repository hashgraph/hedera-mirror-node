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

import javax.annotation.Resource;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.parser.record.ethereum.Eip1559EthereumTransactionParserTest;
import com.hedera.mirror.importer.parser.record.ethereum.LegacyEthereumTransactionParserTest;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.EthereumTransactionRepository;

public class EntityRecordItemListenerEthereumTest extends AbstractEntityRecordItemListenerTest {
    @Resource
    private ContractRepository contractRepository;

    @Resource
    private EthereumTransactionRepository ethereumTransactionRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setEthereumTransactions(true);
    }

    @SneakyThrows
    @Test
    void ethereumTransactionCallLondon() {
        var transactionBytes = Hex.decodeHex(Eip1559EthereumTransactionParserTest.LONDON_RAW_TX);
        RecordItem recordItem = recordItemBuilder.ethereumTransaction(false, CONTRACT_ID, transactionBytes).build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1)
        );
    }

    @SneakyThrows
    @Test
    void ethereumTransactionCallLegacy() {
        var transactionBytes = Hex.decodeHex(LegacyEthereumTransactionParserTest.LEGACY_RAW_TX);
        RecordItem recordItem = recordItemBuilder.ethereumTransaction(false, CONTRACT_ID, transactionBytes).build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1)
        );
    }

    @SneakyThrows
    @Test
    void ethereumTransactionCallEIP155() {
        var transactionBytes = Hex.decodeHex(LegacyEthereumTransactionParserTest.EIP155_RAW_TX);
        RecordItem recordItem = recordItemBuilder.ethereumTransaction(false, CONTRACT_ID, transactionBytes).build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1)
        );
    }

    @SneakyThrows
    @Test
    void ethereumEip1559TransactionCreate() {
        var transactionBytes = Hex.decodeHex(Eip1559EthereumTransactionParserTest.LONDON_RAW_TX);
        RecordItem recordItem = recordItemBuilder.ethereumTransaction(true, CONTRACT_ID, transactionBytes).build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(2, contractRepository.count()), // contract created by transaction
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1)
        );
    }

    @SneakyThrows
    @Test
    void ethereumLegacyTransactionCreate() {
        var transactionBytes = Hex.decodeHex(LegacyEthereumTransactionParserTest.LEGACY_RAW_TX);
        RecordItem recordItem = recordItemBuilder.ethereumTransaction(true, CONTRACT_ID, transactionBytes).build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(2, contractRepository.count()), // contract created by transaction
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
//                () -> assertContractEntity(recordItem),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1)
//                () -> assertContractCreateResult(transactionBody, record)
        );
    }

    @SneakyThrows
    @Test
    void ethereumLegacyChainListTransactionCreate() {
        var transactionBytes = Hex.decodeHex(LegacyEthereumTransactionParserTest.EIP155_RAW_TX);
        RecordItem recordItem = recordItemBuilder.ethereumTransaction(true, CONTRACT_ID, transactionBytes).build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(2, contractRepository.count()), // contract created by transaction
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
//                () -> assertContractEntity(recordItem),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1)
//                () -> assertContractCreateResult(transactionBody, record)
        );
    }
}
