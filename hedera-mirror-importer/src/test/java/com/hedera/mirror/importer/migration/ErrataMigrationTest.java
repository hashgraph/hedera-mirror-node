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

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.IterableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.ErrataType;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.Utility;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
public class ErrataMigrationTest extends IntegrationTest {

    public static final long BAD_TIMESTAMP1 = 1568415600193620000L;
    private static final long BAD_TIMESTAMP2 = 1568528100472477002L;
    private static final long RECEIVER_PAYER_TIMESTAMP = 1570118944399195000L;

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final ContractResultRepository contractResultRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final DomainBuilder domainBuilder;
    private final ErrataMigration errataMigration;
    private final MirrorProperties mirrorProperties;
    private final TransactionRepository transactionRepository;

    @BeforeEach
    void setup() {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.MAINNET);
    }

    @AfterEach
    void teardown() {
        mirrorProperties.setEndDate(Utility.MAX_INSTANT_LONG);
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
        mirrorProperties.setStartDate(Instant.EPOCH);
    }

    @Test
    void migrateNotMainnet() throws Exception {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
        domainBuilder.accountBalanceFile().persist();
        domainBuilder.accountBalanceFile().customize(a -> a.consensusTimestamp(BAD_TIMESTAMP1)).persist();
        spuriousTransfer(1L, 10, TransactionType.CRYPTOTRANSFER, false, false);

        errataMigration.doMigrate();

        assertBalanceOffsets(0);
        assertErrataTransfers(ErrataType.INSERT, 0);
        assertErrataTransfers(ErrataType.DELETE, 0);
        assertErrataTransactions(ErrataType.INSERT, 0);
        assertErrataTransactions(ErrataType.DELETE, 0);
        assertThat(contractResultRepository.count()).isZero();
    }

    @Test
    void migrateOutsideDateRange() throws Exception {
        Instant now = Instant.now();
        mirrorProperties.setStartDate(now);
        mirrorProperties.setEndDate(now.plusSeconds(1L));

        errataMigration.doMigrate();

        assertErrataTransfers(ErrataType.INSERT, 0);
        assertErrataTransactions(ErrataType.INSERT, 0);
        assertThat(contractResultRepository.count()).isZero();
    }

    @Test
    void migrateMainnet() throws Exception {
        domainBuilder.accountBalanceFile().persist();
        domainBuilder.accountBalanceFile().customize(a -> a.consensusTimestamp(BAD_TIMESTAMP1)).persist();
        domainBuilder.accountBalanceFile().customize(a -> a.consensusTimestamp(BAD_TIMESTAMP2)).persist();
        spuriousTransfer(RECEIVER_PAYER_TIMESTAMP, 10, TransactionType.CRYPTOTRANSFER, true, false); // Expected
        spuriousTransfer(1L, 15, TransactionType.CRYPTOTRANSFER, false, false); // Expected
        spuriousTransfer(2L, 15, TransactionType.CRYPTOTRANSFER, false, true); // Expected
        spuriousTransfer(3L, 22, TransactionType.CRYPTOTRANSFER, false, false); // Wrong result
        spuriousTransfer(4L, 10, TransactionType.CRYPTODELETE, true, false); // Wrong type
        spuriousTransfer(1577836799000000000L, 10, TransactionType.CRYPTOTRANSFER, false, false); // Outside period

        errataMigration.doMigrate();

        assertBalanceOffsets(2);
        assertThat(contractResultRepository.count()).isEqualTo(1L);
        assertErrataTransactions(ErrataType.INSERT, 31);
        assertErrataTransactions(ErrataType.DELETE, 0);
        assertErrataTransfers(ErrataType.INSERT, 92);
        assertErrataTransfers(ErrataType.DELETE, 6)
                .extracting(CryptoTransfer::getConsensusTimestamp)
                .containsOnly(1L, 2L, RECEIVER_PAYER_TIMESTAMP);
    }

    @Test
    void migrateWithExistingData() throws Exception {
        var now = DomainUtils.convertToNanosMax(Instant.now());
        domainBuilder.recordFile().customize(r -> r.consensusStart(now)).persist();
        migrateMainnet();
    }

    @Test
    void migrateIdempotency() throws Exception {
        migrateMainnet();
        errataMigration.doMigrate();
        assertBalanceOffsets(2);
        assertErrataTransactions(ErrataType.INSERT, 31);
        assertErrataTransactions(ErrataType.DELETE, 0);
        assertErrataTransfers(ErrataType.INSERT, 92);
        assertErrataTransfers(ErrataType.DELETE, 6);
        assertThat(contractResultRepository.count()).isEqualTo(1L);
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

    private IterableAssert<CryptoTransfer> assertErrataTransfers(ErrataType errata, int expected) {
        return assertThat(cryptoTransferRepository.findAll())
                .filteredOn(c -> c.getErrata() == errata)
                .hasSize(expected);
    }

    private void assertErrataTransactions(ErrataType errata, int expected) {
        assertThat(transactionRepository.findAll())
                .filteredOn(c -> c.getErrata() == errata)
                .hasSize(expected);
    }

    private void spuriousTransfer(long consensusTimestamp, int result, TransactionType type, boolean receiverIsPayer,
                                  boolean senderIsPayer) {
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).result(result).type(type.getProtoId()))
                .persist();
        long amount = 100000L;
        long payer = transaction.getPayerAccountId().getId() + 1000L;
        long sender = senderIsPayer ? payer : domainBuilder.id() + 1000L;
        long receiver = receiverIsPayer ? payer : domainBuilder.id() + 1000L;

        insertCryptoTransfer(transaction, sender, -amount);
        insertCryptoTransfer(transaction, receiver, amount);
        insertCryptoTransfer(transaction, 3L, 1L);
        insertCryptoTransfer(transaction, 98L, 2L);
        insertCryptoTransfer(transaction, 98L, 4L);
        insertCryptoTransfer(transaction, payer, -3L);
        insertCryptoTransfer(transaction, payer, -4L);
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
