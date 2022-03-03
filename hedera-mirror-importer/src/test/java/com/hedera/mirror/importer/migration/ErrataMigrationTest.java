package com.hedera.mirror.importer.migration;

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

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.transaction.ErrataType;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
public class ErrataMigrationTest extends IntegrationTest {

    public static final long BAD_TIMESTAMP1 = 1568415600193620000L;
    private static final long BAD_TIMESTAMP2 = 1568528100472477002L;

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final DomainBuilder domainBuilder;
    private final ErrataMigration errataMigration;
    private final MirrorProperties mirrorProperties;
    private final TransactionRepository transactionRepository;

    private MirrorProperties.HederaNetwork hederaNetwork;

    @BeforeEach
    void setup() {
        hederaNetwork = mirrorProperties.getNetwork();
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.MAINNET);
    }

    @AfterEach
    void teardown() {
        mirrorProperties.setNetwork(hederaNetwork);
    }

    @Test
    void migrateNotMainnet() throws Exception {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
        domainBuilder.accountBalanceFile().persist();
        domainBuilder.accountBalanceFile().customize(a -> a.consensusTimestamp(BAD_TIMESTAMP1)).persist();
        insertCryptoTransfers(1L, 2L);

        errataMigration.doMigrate();

        assertBalanceOffsets(0);
        assertErrataTransfers(ErrataType.INSERT, 0);
        assertErrataTransfers(ErrataType.DELETE, 0);
        assertErrataTransactions(ErrataType.INSERT, 0);
        assertErrataTransactions(ErrataType.DELETE, 0);
    }

    @Test
    void migrateMainnet() throws Exception {
        domainBuilder.accountBalanceFile().persist();
        domainBuilder.accountBalanceFile().customize(a -> a.consensusTimestamp(BAD_TIMESTAMP1)).persist();
        domainBuilder.accountBalanceFile().customize(a -> a.consensusTimestamp(BAD_TIMESTAMP2)).persist();
        insertCryptoTransfers(1L, 1L);
        insertCryptoTransfers(1577836799000000000L, 2L); // Outside errata period

        errataMigration.doMigrate();

        assertBalanceOffsets(2);
        assertErrataTransfers(ErrataType.INSERT, 92);
        assertErrataTransfers(ErrataType.DELETE, 2);
        assertErrataTransactions(ErrataType.INSERT, 31);
        assertErrataTransactions(ErrataType.DELETE, 0);
    }

    @Test
    void onEndWithoutOffset() {
        AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
        accountBalanceFile.setConsensusTimestamp(1L);
        errataMigration.onEnd(accountBalanceFile);
        assertThat(accountBalanceFile.getTimeOffset()).isZero();
    }

    @Test
    void onEndWithOffset() {
        AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
        accountBalanceFile.setConsensusTimestamp(BAD_TIMESTAMP1);
        errataMigration.onStart(); // Call to increase test coverage of no-op methods
        errataMigration.onError();
        errataMigration.onEnd(accountBalanceFile);
        assertThat(accountBalanceFile.getTimeOffset()).isEqualTo(-1);
    }

    @Test
    void onEndNotMainnet() {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
        AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
        accountBalanceFile.setConsensusTimestamp(BAD_TIMESTAMP1);
        errataMigration.onStart(); // Call to increase test coverage of no-op methods
        errataMigration.onError();
        errataMigration.onEnd(accountBalanceFile);
        assertThat(accountBalanceFile.getTimeOffset()).isZero();
    }

    private void assertBalanceOffsets(int expected) {
        assertThat(accountBalanceFileRepository.findAll())
                .filteredOn(a -> a.getTimeOffset() != 0)
                .hasSize(expected);
    }

    private void assertErrataTransfers(ErrataType errata, int expected) {
        assertThat(cryptoTransferRepository.findAll())
                .filteredOn(c -> c.getErrata() == errata)
                .hasSize(expected);
    }

    private void assertErrataTransactions(ErrataType errata, int expected) {
        assertThat(transactionRepository.findAll())
                .filteredOn(c -> c.getErrata() == errata)
                .hasSize(expected);
    }

    private void insertCryptoTransfers(long consensusTimestamp, long entityId) {
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).result(10))
                .persist();
        long amount = 100000L;
        long payer = transaction.getPayerAccountId().getId();

        insertCryptoTransfer(transaction, entityId, amount);
        insertCryptoTransfer(transaction, payer, -amount);
        insertCryptoTransfer(transaction, 3, 1L);
        insertCryptoTransfer(transaction, 98, 2L);
        insertCryptoTransfer(transaction, payer, -1L - 2L - amount);
    }

    private void insertCryptoTransfer(Transaction transaction, long entityId, long amount) {
        domainBuilder.cryptoTransfer()
                .customize(c -> c.amount(amount)
                        .consensusTimestamp(transaction.getConsensusTimestamp())
                        .entityId(entityId)
                        .payerAccountId(transaction.getPayerAccountId()))
                .persist();
    }
}
