package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.aggregator.LogsBloomAggregator;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class BackfillBlockMigrationTest extends IntegrationTest {

    private final BackfillBlockMigration backfillBlockMigration;

    private final RecordFileRepository recordFileRepository;

    private final TransactionRepository transactionRepository;

    @Test
    void empty() {
        backfillBlockMigration.migrateAsync();
        assertThat(recordFileRepository.findAll()).isEmpty();
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var expectedRecordFiles = new ArrayList<RecordFile>();
        var expectedTransactions = new ArrayList<Transaction>();

        // record files in timestamp ascending order:
        // a record file without gas / bloom filter and there is no contract result from the transactions
        var recordFileIndex = 0;
        var consensusStart = domainBuilder.timestamp();
        var consensusEnd = consensusStart + 10L;
        var recordFile = persistRecordFile(recordFileIndex, consensusStart, consensusEnd, -1L, null);
        recordFile.setGasUsed(0L);
        recordFile.setLogsBloom(new byte[0]);
        expectedRecordFiles.add(recordFile);

        var timestamp = consensusStart;
        var transactionIndex = 0;
        var transaction = domainBuilder.transaction().customize(customizeTransaction(timestamp, null)).persist();
        transaction.setIndex(transactionIndex++);
        expectedTransactions.add(transaction);
        timestamp++;
        transaction = domainBuilder.transaction().customize(customizeTransaction(timestamp, null)).persist();
        transaction.setIndex(transactionIndex++);
        expectedTransactions.add(transaction);
        transaction = domainBuilder.transaction().customize(customizeTransaction(consensusEnd, null)).persist();
        transaction.setIndex(transactionIndex);
        expectedTransactions.add(transaction);

        // a record file without gas / bloom filter and there are contract results from the transactions
        // note there is a child contractcreate tx whose contract result has 3 gas_used and null bloom filter
        recordFileIndex++;
        consensusStart = consensusEnd + 1L;
        consensusEnd = consensusStart + 10L;
        recordFile = persistRecordFile(recordFileIndex, consensusStart, consensusEnd, -1L, null);
        var logsBloom = domainBuilder.bloomFilter();
        logsBloom[0] = (byte) 0xf8;
        logsBloom[1] = (byte) 0x87;
        recordFile.setGasUsed(30L);
        recordFile.setLogsBloom(logsBloom);
        expectedRecordFiles.add(recordFile);

        timestamp = consensusStart;
        transactionIndex = 0;
        transaction = domainBuilder.transaction().customize(customizeTransaction(timestamp, null)).persist();
        transaction.setIndex(transactionIndex++);
        expectedTransactions.add(transaction);

        timestamp++;
        transaction = domainBuilder.transaction().customize(customizeTransaction(timestamp, null))
                .customize(this::setContractCall).persist();
        transaction.setIndex(transactionIndex++);
        expectedTransactions.add(transaction);
        var bloom1 = new byte[LogsBloomAggregator.BYTE_SIZE];
        bloom1[0] = (byte) 0xf0;
        bloom1[1] = (byte) 0x07;
        System.arraycopy(logsBloom, 2, bloom1, 2, LogsBloomAggregator.BYTE_SIZE / 2);
        domainBuilder.contractResult().customize(customizeContractResult(timestamp, bloom1, 10L)).persist();

        // child contract create
        timestamp++;
        var parent = transaction;
        transaction = domainBuilder.transaction()
                .customize(customizeTransaction(timestamp, null))
                .customize(t -> t.type(TransactionType.CONTRACTCREATEINSTANCE.getProtoId())
                        .payerAccountId(parent.getPayerAccountId())
                        .validStartNs(parent.getValidStartNs())
                        .nonce(1))
                .persist();
        transaction.setIndex(transactionIndex++);
        expectedTransactions.add(transaction);
        domainBuilder.contractResult().customize(customizeContractResult(timestamp, null, 3L)).persist();

        timestamp++;
        transaction = domainBuilder.transaction().customize(customizeTransaction(timestamp, null))
                .customize(this::setContractCall).persist();
        transaction.setIndex(transactionIndex++);
        expectedTransactions.add(transaction);
        var bloom2 = new byte[LogsBloomAggregator.BYTE_SIZE];
        bloom2[0] = (byte) 0x08;
        bloom2[1] = (byte) 0x80;
        var offset = LogsBloomAggregator.BYTE_SIZE / 2;
        var length = LogsBloomAggregator.BYTE_SIZE - offset;
        System.arraycopy(logsBloom, offset, bloom2, offset, length);
        domainBuilder.contractResult().customize(customizeContractResult(timestamp, bloom2, 20L)).persist();

        transaction = domainBuilder.transaction().customize(customizeTransaction(consensusEnd, null)).persist();
        transaction.setIndex(transactionIndex);
        expectedTransactions.add(transaction);

        // a record file with gas and bloom filter populated
        recordFileIndex++;
        consensusStart = consensusEnd + 2L;
        consensusEnd = consensusStart + 8L;
        logsBloom = domainBuilder.bloomFilter();
        recordFile = persistRecordFile(recordFileIndex, consensusStart, consensusEnd, 40L, logsBloom);
        expectedRecordFiles.add(recordFile);

        expectedTransactions.addAll(List.of(
                domainBuilder.transaction().customize(customizeTransaction(consensusStart, 0)).persist(),
                domainBuilder.transaction().customize(customizeTransaction(consensusStart + 1, 1))
                        .customize(this::setContractCall).persist(),
                domainBuilder.transaction().customize(customizeTransaction(consensusEnd, 2)).persist()
        ));
        domainBuilder.contractResult().customize(customizeContractResult(consensusStart + 1, logsBloom, 40L)).persist();

        // when
        backfillBlockMigration.migrateAsync();

        // then
        assertThat(recordFileRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedRecordFiles);
        assertThat(transactionRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("consensusTimestamp", "index")
                .containsExactlyInAnyOrderElementsOf(expectedTransactions);
    }

    private Consumer<ContractResult.ContractResultBuilder> customizeContractResult(long consensusTimestamp, byte[] bloom,
                                                                                   long gasUsed) {
        return b -> b.consensusTimestamp(consensusTimestamp).bloom(bloom).gasUsed(gasUsed);
    }

    private Consumer<Transaction.TransactionBuilder> customizeTransaction(long consensusTimestamp, Integer index) {
        return b -> b.consensusTimestamp(consensusTimestamp).index(index);
    }

    private RecordFile persistRecordFile(long index, long consensusStart, long consensusEnd, long gasUsed,
                                         byte[] logsBloom) {
        return domainBuilder.recordFile().customize(r -> r.index(index).consensusStart(consensusStart)
                .consensusEnd(consensusEnd).gasUsed(gasUsed).logsBloom(logsBloom)).persist();
    }

    private void setContractCall(Transaction.TransactionBuilder b) {
        b.type(TransactionType.CONTRACTCALL.getProtoId());
    }
}
