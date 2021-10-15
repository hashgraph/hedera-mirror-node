package com.hedera.mirror.importer.migration;

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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.File;
import java.util.List;
import javax.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.repository.NonFeeTransferRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.EntityIdEndec;

@Tag("v1")
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.43.1")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
class TransferTransactionPayerMigrationTest extends IntegrationTest {

    private static final EntityId PAYER_ID = EntityId.of(0, 0, 10001, EntityTypeEnum.ACCOUNT);

    private static final EntityId NODE_ACCOUNT_ID = EntityId.of(0, 0, 3, EntityTypeEnum.ACCOUNT);

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.60.0__add_transfer_payer.sql")
    private File migrationSql;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private TransactionRepository transactionRepository;

    @Resource
    private CryptoTransferRepository cryptoTransferRepository;

    @Resource
    private NftTransferRepository nftTransferRepository;

    @Resource
    private NonFeeTransferRepository nonFeeTransferRepository;

    @Resource
    private TokenTransferRepository tokenTransferRepository;

    @BeforeEach
    void before() {
        revertToPreV_1_44();
    }

    @Test
    void verifyEntityTimestampMigrationEmpty() throws Exception {
        // migration
        migrate();

        assertThat(entityRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
        assertThat(cryptoTransferRepository.count()).isZero();
        assertThat(nftTransferRepository.count()).isZero();
        assertThat(nonFeeTransferRepository.count()).isZero();
        assertThat(tokenTransferRepository.count()).isZero();
    }

    @Test
    void verifyEntityTimestampMigration() throws Exception {
        // given
        entityRepository.saveAll(List.of(
                entity(3, EntityTypeEnum.ACCOUNT, 1L, 2L),
                entity(98, EntityTypeEnum.ACCOUNT, 3L, 4L),
                entity(9000, EntityTypeEnum.ACCOUNT, 99L, 99L),
                entity(9001, EntityTypeEnum.ACCOUNT, 100L, 101L),
                entity(9002, EntityTypeEnum.CONTRACT),
                entity(9003, EntityTypeEnum.FILE),
                entity(9004, EntityTypeEnum.TOPIC),
                entity(9005, EntityTypeEnum.TOKEN),
                entity(9006, EntityTypeEnum.SCHEDULE)
        ));

        transactionRepository.saveAll(List.of(
                transaction(102L, 9001, SUCCESS, TransactionTypeEnum.CRYPTOUPDATEACCOUNT),
                transaction(103L, 9002, SUCCESS, TransactionTypeEnum.CONTRACTCREATEINSTANCE),
                transaction(104L, 9002, INSUFFICIENT_TX_FEE, TransactionTypeEnum.CONTRACTUPDATEINSTANCE),
                transaction(105L, 9003, SUCCESS, TransactionTypeEnum.FILECREATE),
                transaction(106L, 9003, SUCCESS, TransactionTypeEnum.FILEDELETE),
                transaction(107L, 9004, SUCCESS, TransactionTypeEnum.CONSENSUSCREATETOPIC),
                transaction(108L, 9004, SUCCESS, TransactionTypeEnum.CONSENSUSUPDATETOPIC),
                transaction(109L, 9004, SUCCESS, TransactionTypeEnum.CONSENSUSUPDATETOPIC),
                transaction(110L, 9004, SUCCESS, TransactionTypeEnum.CONSENSUSUPDATETOPIC),
                transaction(111L, 9005, SUCCESS, TransactionTypeEnum.TOKENCREATION),
                transaction(112L, 9006, SUCCESS, TransactionTypeEnum.SCHEDULECREATE),
                transaction(200L, 0, SUCCESS, TransactionTypeEnum.CRYPTOTRANSFER),
                transaction(300L, 0, SUCCESS, TransactionTypeEnum.CRYPTOTRANSFER),
                transaction(400L, 0, SUCCESS, TransactionTypeEnum.CRYPTOTRANSFER),
                transaction(500L, 0, SUCCESS, TransactionTypeEnum.CRYPTOTRANSFER)
        ));

        EntityId sender = EntityId.of(0L, 0L, 9000L, ACCOUNT);
        EntityId receiver = EntityId.of(0L, 0L, 9001L, ACCOUNT);
        EntityId node = EntityId.of(0L, 0L, 3L, ACCOUNT);
        EntityId treasury = EntityId.of(0L, 0L, 98L, ACCOUNT);
        EntityId token = EntityId.of(0L, 0L, 9005, TOKEN);
        cryptoTransferRepository.saveAll(List.of(
                // crypto only transfer
                new CryptoTransfer(200L, -40L, sender),
                new CryptoTransfer(200L, 30L, receiver),
                new CryptoTransfer(200L, 5L, node),
                new CryptoTransfer(200L, 5L, treasury),
                // nft transfer
                new CryptoTransfer(300L, -40L, sender),
                new CryptoTransfer(300L, 30L, receiver),
                new CryptoTransfer(300L, 5L, node),
                new CryptoTransfer(300L, 5L, treasury),
                // token transfer
                new CryptoTransfer(400L, -40L, sender),
                new CryptoTransfer(400L, 30L, receiver),
                new CryptoTransfer(400L, 5L, node),
                new CryptoTransfer(400L, 5L, treasury),
                // all transfers
                new CryptoTransfer(500L, -40L, sender),
                new CryptoTransfer(500L, 30L, receiver),
                new CryptoTransfer(500L, 5L, node),
                new CryptoTransfer(500L, 5L, treasury)
        ));

//        nonFeeTransferRepository.saveAll(List.of(
//                // crypto only transfer
//                new NonFeeTransfer(200L, -40L, sender),
//                new NonFeeTransfer(200L, 30L, receiver),
//                // nft transfer
//                new NonFeeTransfer(300L, -40L, sender),
//                new NonFeeTransfer(300L, 30L, receiver),
//                // token transfer
//                new NonFeeTransfer(400L, -40L, sender),
//                new NonFeeTransfer(400L, 30L, receiver),
//                // token transfer
//                new NonFeeTransfer(500L, -40L, sender),
//                new NonFeeTransfer(500L, 30L, receiver)
//        ));
//
//        nftTransferRepository.saveAll(List.of(
//                // nft transfer
//                new NftTransfer(300L, 1L, token, sender, receiver),
//                // nft transfer
//                new NftTransfer(500L, 2L, token, sender, receiver)
//        ));

        tokenTransferRepository.saveAll(List.of(
                // token transfer
                new TokenTransfer(400L, -30, token, sender),
                new TokenTransfer(400L, 30, token, receiver),
                // all transfer
                new TokenTransfer(500L, -30, token, sender),
                new TokenTransfer(500L, 30, token, receiver)
        ));

        // when
        migrate();

        List<TransferSub> expectedCryptoTransfers = List.of(
                // crypto only transfer
                new TransferSub(-40L, 200L, PAYER_ID, null, sender),
                new TransferSub(30L, 200L, PAYER_ID, receiver, null),
                new TransferSub(5L, 200L, PAYER_ID, node, null),
                new TransferSub(5L, 200L, PAYER_ID, treasury, null),
                // nft transfer
                new TransferSub(-40L, 300L, PAYER_ID, null, sender),
                new TransferSub(30L, 300L, PAYER_ID, receiver, null),
                new TransferSub(5L, 300L, PAYER_ID, node, null),
                new TransferSub(5L, 300L, PAYER_ID, treasury, null),
                // token transfer
                new TransferSub(-40L, 400L, PAYER_ID, null, sender),
                new TransferSub(30L, 400L, PAYER_ID, receiver, null),
                new TransferSub(5L, 400L, PAYER_ID, node, null),
                new TransferSub(5L, 400L, PAYER_ID, treasury, null),
                // all transfers
                new TransferSub(-40L, 500L, PAYER_ID, null, sender),
                new TransferSub(30L, 500L, PAYER_ID, receiver, null),
                new TransferSub(5L, 500L, PAYER_ID, node, null),
                new TransferSub(5L, 500L, PAYER_ID, treasury, null)
        );

        List<TransferSub> expectedNftTransfers = List.of(
                // nft transfer
                new TransferSub(1L, 300L, PAYER_ID, receiver, sender),
                new TransferSub(2L, 500L, PAYER_ID, receiver, sender)
        );

        List<TransferSub> expectedNonFeeTransfers = List.of(
                // crypto only transfer
                new TransferSub(-40L, 200L, PAYER_ID, null, sender),
                new TransferSub(30L, 200L, PAYER_ID, receiver, null),
                // nft transfer
                new TransferSub(-40L, 300L, PAYER_ID, null, sender),
                new TransferSub(30L, 300L, PAYER_ID, receiver, null),
                // token transfer
                new TransferSub(-40L, 400L, PAYER_ID, null, sender),
                new TransferSub(30L, 400L, PAYER_ID, receiver, null),
                // token transfer
                new TransferSub(-40L, 500L, PAYER_ID, null, sender),
                new TransferSub(30L, 500L, PAYER_ID, receiver, null)
        );

        List<TransferSub> expectedTokenTransfers = List.of(
                // token transfer
                new TransferSub(-30L, 400L, PAYER_ID, null, sender),
                new TransferSub(30L, 400L, PAYER_ID, receiver, null),
                // all transfer
                new TransferSub(-30L, 500L, PAYER_ID, null, sender),
                new TransferSub(30L, 500L, PAYER_ID, receiver, null)
        );

        // then
        assertThat(findCryptoTransfers())
                .containsExactlyInAnyOrderElementsOf(expectedCryptoTransfers);

        assertThat(findNftTransfers())
                .containsExactlyInAnyOrderElementsOf(expectedNftTransfers);

        assertThat(findNonFeeTransfers())
                .containsExactlyInAnyOrderElementsOf(expectedNonFeeTransfers);

        assertThat(findTokenTransfers())
                .containsExactlyInAnyOrderElementsOf(expectedTokenTransfers);
    }

    private Entity entity(long id, EntityTypeEnum entityTypeEnum) {
        return entity(id, entityTypeEnum, null, null);
    }

    private Entity entity(long id, EntityTypeEnum entityTypeEnum, Long createdTimestamp, Long modifiedTimestamp) {
        Entity entity = EntityIdEndec.decode(id, entityTypeEnum).toEntity();
        entity.setCreatedTimestamp(createdTimestamp);
        entity.setDeleted(false);
        entity.setMemo("foobar");
        entity.setModifiedTimestamp(modifiedTimestamp);
        return entity;
    }

    private Transaction transaction(long consensusNs, long entityNum, ResponseCodeEnum result,
                                    TransactionTypeEnum type) {
        Transaction transaction = new Transaction();
        transaction.setConsensusTimestamp(consensusNs);
        transaction.setEntityId(EntityId.of(0, 0, entityNum, EntityTypeEnum.UNKNOWN));
        transaction.setNodeAccountId(NODE_ACCOUNT_ID);
        transaction.setPayerAccountId(PAYER_ID);
        transaction.setResult(result.getNumber());
        transaction.setType(type.getProtoId());
        transaction.setValidStartNs(consensusNs - 10);
        return transaction;
    }

    private void migrate() throws Exception {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private List<TransferSub> findCryptoTransfers() {
//        return jdbcOperations.query(
//                "select * from crypto_transfer",
//                (rs, rowNum) -> {
//                    CryptoTransfer cryptoTransfer = new CryptoTransfer();
//                    cryptoTransfer.setId(new CryptoTransfer.Id(
//                            rs.getLong("amount"),
//                            rs.getLong("consensus_timestamp"),
//                            EntityIdEndec.decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT)));
//                    return cryptoTransfer;
//                });
        return jdbcOperations.query(
                "select * from crypto_transfer",
                (rs, rowNum) -> {
                    Long amount = rs.getLong("amount");
                    EntityId sender = amount < 0 ? EntityIdEndec
                            .decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT) : null;
                    EntityId receiver = amount > 0 ? EntityIdEndec
                            .decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT) : null;
                    TransferSub transferSub = new TransferSub(
                            rs.getLong("amount"),
                            rs.getLong("consensus_timestamp"),
                            EntityIdEndec.decode(rs.getLong("transaction_payer_account_id"), EntityTypeEnum.ACCOUNT),
                            receiver,
                            sender);
                    return transferSub;
                });
    }

    private List<TransferSub> findNftTransfers() {
//        return jdbcOperations.query(
//                "select * from nft_transfer",
//                (rs, rowNum) -> {
//                    NftTransfer nftTransfer = new NftTransfer();
//                    nftTransfer.setId(new NftTransferId(
//                            rs.getLong("consensus_timestamp"),
//                            rs.getLong("serial_number"),
//                            EntityIdEndec.decode(rs.getLong("token_id"), TOKEN)));
//                    nftTransfer.setReceiverAccountId(EntityIdEndec
//                            .decode(rs.getLong("receiver_account_id"), EntityTypeEnum.ACCOUNT));
//                    nftTransfer.setSenderAccountId(EntityIdEndec
//                            .decode(rs.getLong("sender_account_id"), EntityTypeEnum.ACCOUNT));
//                    return nftTransfer;
//                });
        return jdbcOperations.query(
                "select * from nft_transfer",
                (rs, rowNum) -> {
                    TransferSub transferSub = new TransferSub(
                            rs.getLong("serial_number"),
                            rs.getLong("consensus_timestamp"),
                            EntityIdEndec.decode(rs.getLong("transaction_payer_account_id"), EntityTypeEnum.ACCOUNT),
                            EntityIdEndec.decode(rs.getLong("receiver_account_id"), EntityTypeEnum.ACCOUNT),
                            EntityIdEndec.decode(rs.getLong("sender_account_id"), EntityTypeEnum.ACCOUNT));
                    return transferSub;
                });
    }

    private List<TransferSub> findNonFeeTransfers() {
//        return jdbcOperations.query(
//                "select * from non_fee_transfer",
//                (rs, rowNum) -> {
//                    NonFeeTransfer nonFeeTransfer = new NonFeeTransfer(
//                            rs.getLong("consensus_timestamp"),
//                            rs.getLong("amount"),
//                            EntityIdEndec.decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT));
//                    return nonFeeTransfer;
//                });
        return jdbcOperations.query(
                "select * from non_fee_transfer",
                (rs, rowNum) -> {
                    Long amount = rs.getLong("amount");
                    EntityId sender = amount < 0 ? EntityIdEndec
                            .decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT) : null;
                    EntityId receiver = amount > 0 ? EntityIdEndec
                            .decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT) : null;
                    TransferSub transferSub = new TransferSub(
                            rs.getLong("amount"),
                            rs.getLong("consensus_timestamp"),
                            EntityIdEndec.decode(rs.getLong("transaction_payer_account_id"), EntityTypeEnum.ACCOUNT),
                            receiver,
                            sender);
                    return transferSub;
                });
    }

    private List<TransferSub> findTokenTransfers() {
//        return jdbcOperations.query(
//                "select * from token_transfer",
//                (rs, rowNum) -> {
//                    TokenTransfer tokenTransfer = new TokenTransfer(
//                            rs.getLong("consensus_timestamp"),
//                            rs.getLong("amount"),
//                            EntityIdEndec.decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT),
//                            EntityIdEndec.decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT));
//                    return tokenTransfer;
//                });
        return jdbcOperations.query(
                "select * from token_transfer",
                (rs, rowNum) -> {
                    Long amount = rs.getLong("amount");
                    EntityId sender = amount < 0 ? EntityIdEndec
                            .decode(rs.getLong("account_id"), EntityTypeEnum.ACCOUNT) : null;
                    EntityId receiver = amount > 0 ? EntityIdEndec
                            .decode(rs.getLong("account_id"), EntityTypeEnum.ACCOUNT) : null;
                    TransferSub transferSub = new TransferSub(
                            amount,
                            rs.getLong("consensus_timestamp"),
                            EntityIdEndec.decode(rs.getLong("transaction_payer_account_id"), TOKEN),
                            receiver,
                            sender);
                    return transferSub;
                });
    }

    /**
     * Ensure entity tables match expected state before V_1_44.0
     */
    private void revertToPreV_1_44() {
        // drop transaction_payer_account_id columns
        jdbcOperations
                .execute("alter table if exists crypto_transfer\n" +
                        "    drop column if exists transaction_payer_account_id;");

        jdbcOperations
                .execute("alter table if exists nft_transfer\n" +
                        "    drop column if exists transaction_payer_account_id;");

        jdbcOperations
                .execute("alter table if exists non_fee_transfer\n" +
                        "    drop column if exists transaction_payer_account_id;");

        jdbcOperations
                .execute("alter table if exists token_transfer\n" +
                        "    drop column if exists transaction_payer_account_id;");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private class TransferSub {
        private long amount;
        private long consensusTimeStamp;
        private EntityId payerAccountId;
        private EntityId receiver;
        private EntityId sender;
    }
}
