/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.repository.TransactionRepository;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.80.1")
class NestNftTransferMigrationTest extends IntegrationTest {

    private static final String REVERT_DDL = "alter table transaction drop column if exists nft_transfer";

    private final @Owner JdbcTemplate jdbcTemplate;

    private final TransactionRepository transactionRepository;

    @Value("classpath:db/migration/v1/V1.81.0__nest_nft_transfer.sql")
    private final Resource sql;

    @AfterEach
    void teardown() {
        jdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void empty() {
        runMigration();
        assertTransactions();
    }

    @Test
    void emptyNftTransferAndNonEmptyTransaction() {
        var transaction = persistTransaction(domainBuilder.transaction().get());
        runMigration();
        assertTransactions(transaction);
    }

    @Test
    void migrate() {
        // given
        var alice = domainBuilder.id();
        var bob = domainBuilder.id();
        var treasury = domainBuilder.id();
        var newTreasury = domainBuilder.id();
        var token1 = domainBuilder.id();
        var token2 = domainBuilder.id();

        // mint token1 5 serials
        var tokenMintTx = persistTransaction(domainBuilder
                .transaction()
                .customize(t -> t.type(TransactionType.TOKENMINT.getProtoId()))
                .get());
        var tokenMintTransfers = List.of(
                MigrationNftTransfer.builder()
                        .consensusTimestamp(tokenMintTx.getConsensusTimestamp())
                        .receiverAccountId(treasury)
                        .serialNumber(1)
                        .tokenId(token1)
                        .build(),
                MigrationNftTransfer.builder()
                        .consensusTimestamp(tokenMintTx.getConsensusTimestamp())
                        .receiverAccountId(treasury)
                        .serialNumber(2)
                        .tokenId(token1)
                        .build(),
                MigrationNftTransfer.builder()
                        .consensusTimestamp(tokenMintTx.getConsensusTimestamp())
                        .receiverAccountId(treasury)
                        .serialNumber(3)
                        .tokenId(token1)
                        .build(),
                MigrationNftTransfer.builder()
                        .consensusTimestamp(tokenMintTx.getConsensusTimestamp())
                        .receiverAccountId(treasury)
                        .serialNumber(4)
                        .tokenId(token1)
                        .build(),
                MigrationNftTransfer.builder()
                        .consensusTimestamp(tokenMintTx.getConsensusTimestamp())
                        .receiverAccountId(treasury)
                        .serialNumber(5)
                        .tokenId(token1)
                        .build());
        persistNftTransfers(tokenMintTransfers);
        // expected
        tokenMintTx.setNftTransfer(toDomainNftTransfers(tokenMintTransfers));

        // crypto transfer 1 using allowance
        var cryptoTransferTx1 = persistTransaction(domainBuilder.transaction().get());
        var cryptoTransfer1NftTransfers = List.of(
                MigrationNftTransfer.builder()
                        .consensusTimestamp(cryptoTransferTx1.getConsensusTimestamp())
                        .isApproval(true)
                        .receiverAccountId(alice)
                        .senderAccountId(treasury)
                        .serialNumber(1)
                        .tokenId(token1)
                        .build(),
                MigrationNftTransfer.builder()
                        .consensusTimestamp(cryptoTransferTx1.getConsensusTimestamp())
                        .isApproval(true)
                        .receiverAccountId(bob)
                        .senderAccountId(treasury)
                        .serialNumber(2)
                        .tokenId(token1)
                        .build());
        persistNftTransfers(cryptoTransfer1NftTransfers);
        // expected
        cryptoTransferTx1.setNftTransfer(toDomainNftTransfers(cryptoTransfer1NftTransfers));

        // crypto transfer 2
        var cryptoTransferTx2 = persistTransaction(domainBuilder.transaction().get());
        var cryptoTransfer2NftTransfers = List.of(
                MigrationNftTransfer.builder()
                        .consensusTimestamp(cryptoTransferTx2.getConsensusTimestamp())
                        .isApproval(false)
                        .receiverAccountId(alice)
                        .senderAccountId(treasury)
                        .serialNumber(3)
                        .tokenId(token1)
                        .build(),
                MigrationNftTransfer.builder()
                        .consensusTimestamp(cryptoTransferTx2.getConsensusTimestamp())
                        .isApproval(false)
                        .receiverAccountId(alice)
                        .senderAccountId(treasury)
                        .serialNumber(1)
                        .tokenId(token2) // different token
                        .build());
        persistNftTransfers(cryptoTransfer2NftTransfers);
        // expected
        cryptoTransferTx2.setNftTransfer(toDomainNftTransfers(cryptoTransfer2NftTransfers));

        // wipe token1 serial 2 from bob
        var tokenWipeTx = persistTransaction(domainBuilder
                .transaction()
                .customize(t -> t.type(TransactionType.TOKENWIPE.getProtoId()))
                .get());
        var tokenWipeNftTransfers = List.of(MigrationNftTransfer.builder()
                .consensusTimestamp(tokenWipeTx.getConsensusTimestamp())
                .senderAccountId(bob)
                .serialNumber(2)
                .tokenId(token1)
                .build());
        persistNftTransfers(tokenWipeNftTransfers);
        // expected
        tokenWipeTx.setNftTransfer(toDomainNftTransfers(tokenWipeNftTransfers));

        // treasury change, new treasury will own serial 4 and serial 5
        var tokenUpdateTx = persistTransaction(domainBuilder
                .transaction()
                .customize(t -> t.type(TransactionType.TOKENUPDATE.getProtoId()))
                .get());
        // before nft transfers were nested, the importer will create per serial nft transfer for nft treasury change
        var tokenUpdateNftTransfers = List.of(
                MigrationNftTransfer.builder()
                        .consensusTimestamp(tokenUpdateTx.getConsensusTimestamp())
                        .receiverAccountId(newTreasury)
                        .senderAccountId(treasury)
                        .serialNumber(4)
                        .tokenId(token1)
                        .build(),
                MigrationNftTransfer.builder()
                        .consensusTimestamp(tokenUpdateTx.getConsensusTimestamp())
                        .receiverAccountId(newTreasury)
                        .senderAccountId(treasury)
                        .serialNumber(5)
                        .tokenId(token1)
                        .build());
        persistNftTransfers(tokenUpdateNftTransfers);
        // expected
        tokenUpdateTx.setNftTransfer(List.of(NftTransfer.builder()
                .isApproval(false)
                .receiverAccountId(EntityId.of(newTreasury, ACCOUNT))
                .senderAccountId(EntityId.of(treasury, ACCOUNT))
                .serialNumber(NftTransfer.WILDCARD_SERIAL_NUMBER)
                .tokenId(EntityId.of(token1, TOKEN))
                .build()));

        // at certain point, token1 and token 2 are both deleted, later alice dissociate herself from both.
        // Note alice owns the following before the dissociation
        //   - token1 serial 1 and serial 3
        //   - token2 serial
        var tokenDissociateTx = persistTransaction(domainBuilder
                .transaction()
                .customize(t -> t.type(TransactionType.TOKENDISSOCIATE.getProtoId()))
                .get());
        var tokenDissociateNftTransfers = List.of(
                MigrationNftTransfer.builder()
                        .consensusTimestamp(tokenDissociateTx.getConsensusTimestamp())
                        .senderAccountId(alice)
                        .serialNumber(1)
                        .tokenId(token1)
                        .build(),
                MigrationNftTransfer.builder()
                        .consensusTimestamp(tokenDissociateTx.getConsensusTimestamp())
                        .senderAccountId(alice)
                        .serialNumber(3)
                        .tokenId(token1)
                        .build(),
                MigrationNftTransfer.builder()
                        .consensusTimestamp(tokenDissociateTx.getConsensusTimestamp())
                        .senderAccountId(alice)
                        .serialNumber(1)
                        .tokenId(token2)
                        .build());
        persistNftTransfers(tokenDissociateNftTransfers);
        // expected, note the serial number indicates the number of nfts burned and it's negative
        tokenDissociateTx.setNftTransfer(List.of(
                NftTransfer.builder()
                        .isApproval(false)
                        .senderAccountId(EntityId.of(alice, ACCOUNT))
                        .serialNumber(-2L)
                        .tokenId(EntityId.of(token1, TOKEN))
                        .build(),
                NftTransfer.builder()
                        .isApproval(false)
                        .senderAccountId(EntityId.of(alice, ACCOUNT))
                        .serialNumber(-1L)
                        .tokenId(EntityId.of(token2, TOKEN))
                        .build()));

        // when
        runMigration();

        // then
        assertTransactions(
                tokenMintTx, cryptoTransferTx1, cryptoTransferTx2, tokenWipeTx, tokenUpdateTx, tokenDissociateTx);
    }

    private void assertTransactions(Transaction... transactions) {
        assertThat(transactionRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("consensus_timestamp", "nft_transfer")
                .containsExactlyInAnyOrder(transactions);
    }

    private void persistNftTransfers(List<MigrationNftTransfer> nftTransfers) {
        // hardcode payer account id, the column is "not null" and it's not needed for the migration
        jdbcTemplate.batchUpdate(
                """
            insert into nft_transfer (consensus_timestamp, is_approval, payer_account_id, receiver_account_id,
              sender_account_id, serial_number, token_id)
              values (?, ?, 5000, ?, ?, ?, ?)
            """,
                nftTransfers,
                nftTransfers.size(),
                (ps, nftTransfer) -> {
                    ps.setLong(1, nftTransfer.getConsensusTimestamp());
                    ps.setBoolean(2, nftTransfer.isApproval());
                    setNullableId(ps, 3, nftTransfer.getReceiverAccountId());
                    setNullableId(ps, 4, nftTransfer.getSenderAccountId());
                    ps.setLong(5, nftTransfer.getSerialNumber());
                    ps.setLong(6, nftTransfer.getTokenId());
                });
    }

    private Transaction persistTransaction(Transaction transaction) {
        jdbcTemplate.update(
                """
                        insert into transaction (consensus_timestamp, payer_account_id, result, type, valid_start_ns)
                        values (?, ?, ?, ?, ?)
                        """,
                transaction.getConsensusTimestamp(),
                transaction.getPayerAccountId().getId(),
                transaction.getResult(),
                transaction.getType(),
                transaction.getValidStartNs());
        return transaction;
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = sql.getInputStream()) {
            jdbcTemplate.update(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
        }
    }

    private void setNullableId(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }

    private List<NftTransfer> toDomainNftTransfers(List<MigrationNftTransfer> nftTransfers) {
        return nftTransfers.stream()
                .map(MigrationNftTransfer::toDomainNftTransfer)
                .toList();
    }

    @AllArgsConstructor
    @Builder
    @Data
    private static class MigrationNftTransfer {
        long consensusTimestamp;
        boolean isApproval;
        Long receiverAccountId;
        Long senderAccountId;
        long serialNumber;
        long tokenId;

        public NftTransfer toDomainNftTransfer() {
            var receiver = receiverAccountId != null ? EntityId.of(receiverAccountId, ACCOUNT) : null;
            var sender = senderAccountId != null ? EntityId.of(senderAccountId, ACCOUNT) : null;
            return NftTransfer.builder()
                    .isApproval(isApproval)
                    .receiverAccountId(receiver)
                    .senderAccountId(sender)
                    .serialNumber(serialNumber)
                    .tokenId(EntityId.of(tokenId, TOKEN))
                    .build();
        }
    }
}
