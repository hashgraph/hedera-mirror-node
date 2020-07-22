package com.hedera.mirror.importer.parser.record.entity.sql;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

class PgCopyTest extends IntegrationTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Resource
    private DataSource dataSource;
    @Resource
    private CryptoTransferRepository cryptoTransferRepository;
    @Resource
    private TransactionRepository transactionRepository;

    private PgCopy<CryptoTransfer> cryptoTransferPgCopy;
    private PgCopy<Transaction> transactionPgCopy;

    @Resource
    private SqlProperties sqlProperties;

    @BeforeEach
    void beforeEach() throws Exception {
        cryptoTransferPgCopy = new PgCopy<>(dataSource, CryptoTransfer.class, meterRegistry, sqlProperties
                .getBatchSize());
        transactionPgCopy = new PgCopy<>(dataSource, Transaction.class, meterRegistry, sqlProperties
                .getBatchSize());
    }

    @Test
    void testCopy() throws SQLException {
        var cryptoTransfers = new HashSet<CryptoTransfer>();
        cryptoTransfers.add(cryptoTransfer(1));
        cryptoTransfers.add(cryptoTransfer(2));
        cryptoTransfers.add(cryptoTransfer(3));

        cryptoTransferPgCopy.copy(cryptoTransfers, dataSource.getConnection());

        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(cryptoTransfers);
    }

    @Test
    void testCopyDuplicates() throws SQLException {
        var transactions = new HashSet<Transaction>();
        transactions.add(transaction(1));
        transactions.add(transaction(2));
        transactions.add(transaction(2));// duplicate transaction to be ignored with no error on attempted copy
        transactions.add(transaction(3));

        transactionPgCopy.copy(transactions, dataSource.getConnection());

        assertThat(transactionRepository.findAll()).hasSize(3).containsExactlyInAnyOrderElementsOf(transactions);
    }

    @Test
    void throwsParserException() throws SQLException, IOException {
        // given
        CopyManager copyManager = mock(CopyManager.class);
        doThrow(SQLException.class).when(copyManager).copyIn(any(), (Reader) any(), anyInt());
        PGConnection pgConnection = mock(PGConnection.class);
        doReturn(copyManager).when(pgConnection).getCopyAPI();
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        doReturn(conn).when(dataSource).getConnection();
        doReturn(pgConnection).when(conn).unwrap(any());
        var cryptoTransferPgCopy2 = new PgCopy<>(dataSource, CryptoTransfer.class, meterRegistry, sqlProperties
                .getBatchSize());
        var cryptoTransfers = new HashSet<CryptoTransfer>();
        cryptoTransfers.add(cryptoTransfer(1));
        // when
        assertThatThrownBy(() -> cryptoTransferPgCopy2.copy(cryptoTransfers, dataSource.getConnection()))
                .isInstanceOf(ParserException.class);
    }

    @Test
    void testNullItems() throws SQLException {
        cryptoTransferPgCopy.copy(null, dataSource.getConnection());
        assertThat(cryptoTransferRepository.count()).isEqualTo(0);
    }

    private CryptoTransfer cryptoTransfer(long consensusTimestamp) {
        return new CryptoTransfer(consensusTimestamp, 1L, EntityId.of(0L, 1L, 2L, EntityTypeEnum.ACCOUNT));
    }

    private Transaction transaction(long consensusNs) {
        EntityId entityId = EntityId.of(10, 10, 10, ACCOUNT);
        Transaction transaction = new Transaction();
        transaction.setConsensusNs(consensusNs);
        transaction.setEntityId(entityId);
        transaction.setNodeAccountId(entityId);
        transaction.setMemo("memo".getBytes());
        transaction.setType(14);
        transaction.setResult(22);
        transaction.setTransactionHash("transaction hash".getBytes());
        transaction.setTransactionBytes("transaction bytes".getBytes());
        transaction.setPayerAccountId(entityId);
        transaction.setValidStartNs(1L);
        transaction.setValidDurationSeconds(1L);
        transaction.setMaxFee(1L);
        transaction.setChargedTxFee(1L);
        transaction.setInitialBalance(0L);
        return transaction;
    }
}
