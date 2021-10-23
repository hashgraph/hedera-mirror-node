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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AssessedCustomFee;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.DomainBuilder;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
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

@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.46.6")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
class TransferTransactionPayerMigrationTest extends IntegrationTest {

    private static final EntityId PAYER_ID = EntityId.of(0, 0, 10001, EntityTypeEnum.ACCOUNT);

    private static final EntityId NODE_ACCOUNT_ID = EntityId.of(0, 0, 3, EntityTypeEnum.ACCOUNT);

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.47.0__add_transfer_payer.sql")
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

    @Resource
    private DomainBuilder domainBuilder;

    @BeforeEach
    void before() {
        revertToPreV_1_47();
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
        Entity node = domainBuilder.entity().customize(e -> e
                .createdTimestamp(10L)
                .timestampRange(Range.atLeast(10L))).get();
        Entity treasury = domainBuilder.entity().customize(e -> e
                .createdTimestamp(20L)
                .timestampRange(Range.atLeast(20L))).get();
        Entity sender = domainBuilder.entity().customize(e -> e
                .createdTimestamp(30L)
                .timestampRange(Range.atLeast(30L))).get();
        Entity receiver = domainBuilder.entity().customize(e -> e
                .createdTimestamp(40L)
                .timestampRange(Range.atLeast(40L))).get();
        Entity contract = domainBuilder.entity().customize(e -> e
                .createdTimestamp(50L)
                .timestampRange(Range.atLeast(50L))
                .type(EntityTypeEnum.CONTRACT.getId())).get();
        Entity file = domainBuilder.entity().customize(e -> e
                .createdTimestamp(60L)
                .timestampRange(Range.atLeast(60L))
                .type(EntityTypeEnum.FILE.getId())).get();
        Entity topic = domainBuilder.entity().customize(e -> e
                .createdTimestamp(70L)
                .timestampRange(Range.atLeast(70L))
                .type(EntityTypeEnum.TOPIC.getId())).get();
        Entity token = domainBuilder.entity().customize(e -> e
                .createdTimestamp(80L)
                .timestampRange(Range.atLeast(80L))
                .type(TOKEN.getId())).get();
        Entity schedule = domainBuilder.entity().customize(e -> e
                .createdTimestamp(90L)
                .timestampRange(Range.atLeast(90L))
                .type(EntityTypeEnum.SCHEDULE.getId())).get();

        // given
        entityRepository.saveAll(List.of(
                node,
                treasury,
                sender,
                receiver,
                contract,
                file,
                topic,
                token,
                schedule
        ));

        Transaction transfer1 = transaction(schedule
                .getCreatedTimestamp() + 200L, 0, SUCCESS, TransactionTypeEnum.CRYPTOTRANSFER);
        Transaction transfer2 = transaction(schedule
                .getCreatedTimestamp() + 300L, 0, SUCCESS, TransactionTypeEnum.CRYPTOTRANSFER);
        Transaction transfer3 = transaction(schedule
                .getCreatedTimestamp() + 400L, 0, SUCCESS, TransactionTypeEnum.CRYPTOTRANSFER);
        Transaction transfer4 = transaction(schedule
                .getCreatedTimestamp() + 500L, 0, SUCCESS, TransactionTypeEnum.CRYPTOTRANSFER);
        Transaction transfer5 = transaction(schedule
                .getCreatedTimestamp() + 600L, 0, SUCCESS, TransactionTypeEnum.CRYPTOTRANSFER);
        transactionRepository.saveAll(List.of(
                transaction(contract.getCreatedTimestamp(), contract
                        .getId(), SUCCESS, TransactionTypeEnum.CONTRACTCREATEINSTANCE),
                transaction(contract.getCreatedTimestamp() + 1, contract
                        .getId(), INSUFFICIENT_TX_FEE, TransactionTypeEnum.CONTRACTUPDATEINSTANCE),
                transaction(file.getCreatedTimestamp(), file.getId(), SUCCESS, TransactionTypeEnum.FILECREATE),
                transaction(file.getCreatedTimestamp() + 1, file.getId(), SUCCESS, TransactionTypeEnum.FILEDELETE),
                transaction(topic.getCreatedTimestamp(), topic
                        .getId(), SUCCESS, TransactionTypeEnum.CONSENSUSCREATETOPIC),
                transaction(topic.getCreatedTimestamp() + 1, topic
                        .getId(), SUCCESS, TransactionTypeEnum.CONSENSUSUPDATETOPIC),
                transaction(topic.getCreatedTimestamp() + 2, topic
                        .getId(), SUCCESS, TransactionTypeEnum.CONSENSUSUPDATETOPIC),
                transaction(topic.getCreatedTimestamp() + 3, topic
                        .getId(), SUCCESS, TransactionTypeEnum.CONSENSUSUPDATETOPIC),
                transaction(token.getCreatedTimestamp(), token.getId(), SUCCESS, TransactionTypeEnum.TOKENCREATION),
                transaction(schedule.getCreatedTimestamp(), schedule
                        .getId(), SUCCESS, TransactionTypeEnum.SCHEDULECREATE),
                transfer1,
                transfer2,
                transfer3,
                transfer4,
                transfer5
        ));

        EntityId nodeId = node.toEntityId();
        EntityId treasuryId = treasury.toEntityId();
        EntityId senderId = sender.toEntityId();
        EntityId receiverId = receiver.toEntityId();
        EntityId tokenId = token.toEntityId();
        long senderPaymentAmount = -45L;
        long receivedAmount = 30L;
        long nodePaymentAmount = 10L;
        long treasuryPaymentAmount = 5L;

        persistAssessedCustomFees(List.of(
                // assessed custom fee transfer
                new AssessedCustomFee(new AssessedCustomFee.Id(receiverId, transfer1.getConsensusTimestamp()),
                        receivedAmount, List.of(senderId.getId()), tokenId, null),
                // all transfers
                new AssessedCustomFee(new AssessedCustomFee.Id(receiverId, transfer5.getConsensusTimestamp()),
                        receivedAmount, List.of(senderId.getId()), tokenId, null)
        ));

        persistCryptoTransfers(List.of(
                // assessed custom fee transfer
                new CryptoTransfer(transfer1.getConsensusTimestamp(), senderPaymentAmount, senderId),
                new CryptoTransfer(transfer1.getConsensusTimestamp(), receivedAmount, receiverId),
                new CryptoTransfer(transfer1.getConsensusTimestamp(), nodePaymentAmount, nodeId),
                new CryptoTransfer(transfer1.getConsensusTimestamp(), treasuryPaymentAmount, treasuryId),
                // crypto only transfer
                new CryptoTransfer(transfer2.getConsensusTimestamp(), senderPaymentAmount, senderId),
                new CryptoTransfer(transfer2.getConsensusTimestamp(), receivedAmount, receiverId),
                new CryptoTransfer(transfer2.getConsensusTimestamp(), nodePaymentAmount, nodeId),
                new CryptoTransfer(transfer2.getConsensusTimestamp(), treasuryPaymentAmount, treasuryId),
                // nft transfer
                new CryptoTransfer(transfer3.getConsensusTimestamp(), senderPaymentAmount, senderId),
                new CryptoTransfer(transfer3.getConsensusTimestamp(), receivedAmount, receiverId),
                new CryptoTransfer(transfer3.getConsensusTimestamp(), nodePaymentAmount, nodeId),
                new CryptoTransfer(transfer3.getConsensusTimestamp(), treasuryPaymentAmount, treasuryId),
                // token transfer
                new CryptoTransfer(transfer4.getConsensusTimestamp(), senderPaymentAmount, senderId),
                new CryptoTransfer(transfer4.getConsensusTimestamp(), receivedAmount, receiverId),
                new CryptoTransfer(transfer4.getConsensusTimestamp(), nodePaymentAmount, nodeId),
                new CryptoTransfer(transfer4.getConsensusTimestamp(), treasuryPaymentAmount, treasuryId),
                // all transfers
                new CryptoTransfer(transfer5.getConsensusTimestamp(), senderPaymentAmount, senderId),
                new CryptoTransfer(transfer5.getConsensusTimestamp(), receivedAmount, receiverId),
                new CryptoTransfer(transfer5.getConsensusTimestamp(), nodePaymentAmount, nodeId),
                new CryptoTransfer(transfer5.getConsensusTimestamp(), treasuryPaymentAmount, treasuryId)
        ));

        persistNonFeeTransfers(List.of(
                // assessed custom fee only transfer
                new NonFeeTransfer(transfer1.getConsensusTimestamp(), senderPaymentAmount, senderId),
                new NonFeeTransfer(transfer1.getConsensusTimestamp(), receivedAmount, receiverId),
                // crypto only transfer
                new NonFeeTransfer(transfer2.getConsensusTimestamp(), senderPaymentAmount, senderId),
                new NonFeeTransfer(transfer2.getConsensusTimestamp(), receivedAmount, receiverId),
                // nft transfer
                new NonFeeTransfer(transfer3.getConsensusTimestamp(), senderPaymentAmount, senderId),
                new NonFeeTransfer(transfer3.getConsensusTimestamp(), receivedAmount, receiverId),
                // token transfer
                new NonFeeTransfer(transfer4.getConsensusTimestamp(), senderPaymentAmount, senderId),
                new NonFeeTransfer(transfer4.getConsensusTimestamp(), receivedAmount, receiverId),
                // all transfers
                new NonFeeTransfer(transfer5.getConsensusTimestamp(), senderPaymentAmount, senderId),
                new NonFeeTransfer(transfer5.getConsensusTimestamp(), receivedAmount, receiverId)
        ));

        persistNftTransfers(List.of(
                // nft transfer
                new NftTransfer(transfer3.getConsensusTimestamp(), 1L, tokenId, senderId, receiverId, null),
                // all transfers
                new NftTransfer(transfer5.getConsensusTimestamp(), 2L, tokenId, senderId, receiverId, null)
        ));

        persistTokenTransfers(List.of(
                // token transfer
                new TokenTransfer(transfer4.getConsensusTimestamp(), -receivedAmount, tokenId, senderId),
                new TokenTransfer(transfer4.getConsensusTimestamp(), receivedAmount, tokenId, receiverId),
                // all transfers
                new TokenTransfer(transfer5.getConsensusTimestamp(), -receivedAmount, tokenId, senderId),
                new TokenTransfer(transfer5.getConsensusTimestamp(), receivedAmount, tokenId, receiverId)
        ));

        // when
        migrate();

        List<SharedTransfer> expectedAssessedCustomFeeTransfers = List.of(
                // assessed custom fee transfer
                new SharedTransfer(receivedAmount, transfer1.getConsensusTimestamp(), PAYER_ID, receiverId, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, senderId)
        );

        List<SharedTransfer> expectedCryptoTransfers = List.of(
                // assessed custom fee transfer
                new SharedTransfer(senderPaymentAmount, transfer1.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer1.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer1.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(treasuryPaymentAmount, transfer1
                        .getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // crypto only transfer
                new SharedTransfer(senderPaymentAmount, transfer2.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer2.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer2.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(treasuryPaymentAmount, transfer2
                        .getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // nft transfer
                new SharedTransfer(senderPaymentAmount, transfer3.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer3.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer3.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(treasuryPaymentAmount, transfer3
                        .getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // token transfer
                new SharedTransfer(senderPaymentAmount, transfer4.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer4.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(treasuryPaymentAmount, transfer4
                        .getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // all transfers
                new SharedTransfer(senderPaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(treasuryPaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, treasuryId, null)
        );

        List<SharedTransfer> expectedNftTransfers = List.of(
                // nft transfer
                new SharedTransfer(1L, transfer3.getConsensusTimestamp(), PAYER_ID, receiverId, senderId),
                new SharedTransfer(2L, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, senderId)
        );

        List<SharedTransfer> expectedNonFeeTransfers = List.of(
                // assessed custom fee only transfer
                new SharedTransfer(senderPaymentAmount, transfer1.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer1.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // crypto only transfer
                new SharedTransfer(senderPaymentAmount, transfer2.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer2.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // nft transfer
                new SharedTransfer(senderPaymentAmount, transfer3.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer3.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // token transfer
                new SharedTransfer(senderPaymentAmount, transfer4.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // token transfer
                new SharedTransfer(senderPaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, null)
        );

        List<SharedTransfer> expectedTokenTransfers = List.of(
                // token transfer
                new SharedTransfer(-receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // all transfer
                new SharedTransfer(-receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, null)
        );

        // then
        assertThat(findAssessedCustomFees())
                .containsExactlyInAnyOrderElementsOf(expectedAssessedCustomFeeTransfers);

        assertThat(findCryptoTransfers())
                .containsExactlyInAnyOrderElementsOf(expectedCryptoTransfers);

        assertThat(findNftTransfers())
                .containsExactlyInAnyOrderElementsOf(expectedNftTransfers);

        assertThat(findNonFeeTransfers())
                .containsExactlyInAnyOrderElementsOf(expectedNonFeeTransfers);

        assertThat(findTokenTransfers())
                .containsExactlyInAnyOrderElementsOf(expectedTokenTransfers);
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

    private void persistAssessedCustomFees(List<AssessedCustomFee> assessedCustomFees) throws IOException {
        for (AssessedCustomFee assessedCustomFee : assessedCustomFees) {
            var id = assessedCustomFee.getId();
            jdbcOperations.update(
                    "insert into assessed_custom_fee (amount, collector_account_id, consensus_timestamp, token_id," +
                            "effective_payer_account_ids)" +
                            " values (?,?,?,?,?::bigint[])",
                    assessedCustomFee.getAmount(),
                    id.getCollectorAccountId().getId(),
                    id.getConsensusTimestamp(),
                    assessedCustomFee.getTokenId().getId(),
                    longListSerializer(assessedCustomFee.getEffectivePayerAccountIds())
            );
        }
    }

    private void persistCryptoTransfers(List<CryptoTransfer> cryptoTransfers) {
        for (CryptoTransfer cryptoTransfer : cryptoTransfers) {
            var id = cryptoTransfer.getId();
            jdbcOperations.update(
                    "insert into crypto_transfer (amount, consensus_timestamp, entity_id)" +
                            " values (?,?,?)",
                    id.getAmount(),
                    id.getConsensusTimestamp(),
                    id.getEntityId().getId()
            );
        }
    }

    private void persistNftTransfers(List<NftTransfer> nftTransfers) {
        for (NftTransfer nftTransfer : nftTransfers) {
            var id = nftTransfer.getId();
            jdbcOperations.update(
                    "insert into nft_transfer (consensus_timestamp, receiver_account_id, sender_account_id, " +
                            "serial_number, token_id)" +
                            " values (?,?,?,?,?)",
                    id.getConsensusTimestamp(),
                    nftTransfer.getReceiverAccountId().getId(),
                    nftTransfer.getSenderAccountId().getId(),
                    id.getSerialNumber(),
                    id.getTokenId().getId()
            );
        }
    }

    private void persistNonFeeTransfers(List<NonFeeTransfer> nonFeeTransfers) {
        for (NonFeeTransfer nonFeeTransfer : nonFeeTransfers) {
            var id = nonFeeTransfer.getId();
            jdbcOperations.update(
                    "insert into non_fee_transfer (amount, entity_id, consensus_timestamp)" +
                            " values (?,?,?)",
                    nonFeeTransfer.getAmount(),
                    id.getEntityId().getId(),
                    id.getConsensusTimestamp()
            );
        }
    }

    private void persistTokenTransfers(List<TokenTransfer> tokenTransfers) {
        for (TokenTransfer tokenTransfer : tokenTransfers) {
            var id = tokenTransfer.getId();
            jdbcOperations.update(
                    "insert into token_transfer (amount, account_id, consensus_timestamp, token_id)" +
                            " values (?,?,?,?)",
                    tokenTransfer.getAmount(),
                    id.getAccountId().getId(),
                    id.getConsensusTimestamp(),
                    id.getTokenId().getId()
            );
        }
    }

    private List<SharedTransfer> findAssessedCustomFees() {
        return jdbcOperations.query(
                "select * from assessed_custom_fee",
                (rs, rowNum) -> {
                    List<Long> effectivePayers = Arrays
                            .asList((Long[]) rs.getArray("effective_payer_account_ids").getArray());
                    EntityId sender = ObjectUtils.isNotEmpty(effectivePayers) ? EntityIdEndec
                            .decode(effectivePayers.get(0), EntityTypeEnum.ACCOUNT) : null;
//                            String senderString = StringUtils.split(rs.getString("effective_payer_account_ids"))[0];
//                    EntityId sender = StringUtils.isNotEmpty(senderString) ? EntityIdEndec
//                            .decode(Integer.valueOf(senderString), EntityTypeEnum.ACCOUNT) : null;
                    EntityId receiver = EntityIdEndec
                            .decode(rs.getLong("collector_account_id"), EntityTypeEnum.ACCOUNT);
                    SharedTransfer sharedTransfer = new SharedTransfer(
                            rs.getLong("amount"),
                            rs.getLong("consensus_timestamp"),
                            EntityIdEndec.decode(rs.getLong("payer_account_id"), EntityTypeEnum.ACCOUNT),
                            receiver,
                            sender);
                    return sharedTransfer;
                });
    }

    private List<SharedTransfer> findCryptoTransfers() {
        return jdbcOperations.query(
                "select * from crypto_transfer",
                (rs, rowNum) -> {
                    Long amount = rs.getLong("amount");
                    EntityId sender = amount < 0 ? EntityIdEndec
                            .decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT) : null;
                    EntityId receiver = amount > 0 ? EntityIdEndec
                            .decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT) : null;
                    SharedTransfer sharedTransfer = new SharedTransfer(
                            amount,
                            rs.getLong("consensus_timestamp"),
                            EntityIdEndec.decode(rs.getLong("payer_account_id"), EntityTypeEnum.ACCOUNT),
                            receiver,
                            sender);
                    return sharedTransfer;
                });
    }

    private List<SharedTransfer> findNftTransfers() {
        return jdbcOperations.query(
                "select * from nft_transfer",
                (rs, rowNum) -> {
                    SharedTransfer sharedTransfer = new SharedTransfer(
                            rs.getLong("serial_number"),
                            rs.getLong("consensus_timestamp"),
                            EntityIdEndec.decode(rs.getLong("payer_account_id"), EntityTypeEnum.ACCOUNT),
                            EntityIdEndec.decode(rs.getLong("receiver_account_id"), EntityTypeEnum.ACCOUNT),
                            EntityIdEndec.decode(rs.getLong("sender_account_id"), EntityTypeEnum.ACCOUNT));
                    return sharedTransfer;
                });
    }

    private List<SharedTransfer> findNonFeeTransfers() {
        return jdbcOperations.query(
                "select * from non_fee_transfer",
                (rs, rowNum) -> {
                    Long amount = rs.getLong("amount");
                    EntityId sender = amount < 0 ? EntityIdEndec
                            .decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT) : null;
                    EntityId receiver = amount > 0 ? EntityIdEndec
                            .decode(rs.getLong("entity_id"), EntityTypeEnum.ACCOUNT) : null;
                    SharedTransfer sharedTransfer = new SharedTransfer(
                            amount,
                            rs.getLong("consensus_timestamp"),
                            EntityIdEndec.decode(rs.getLong("payer_account_id"), EntityTypeEnum.ACCOUNT),
                            receiver,
                            sender);
                    return sharedTransfer;
                });
    }

    private List<SharedTransfer> findTokenTransfers() {
        return jdbcOperations.query(
                "select * from token_transfer",
                (rs, rowNum) -> {
                    Long amount = rs.getLong("amount");
                    EntityId sender = amount < 0 ? EntityIdEndec
                            .decode(rs.getLong("account_id"), EntityTypeEnum.ACCOUNT) : null;
                    EntityId receiver = amount > 0 ? EntityIdEndec
                            .decode(rs.getLong("account_id"), EntityTypeEnum.ACCOUNT) : null;
                    SharedTransfer sharedTransfer = new SharedTransfer(
                            amount,
                            rs.getLong("consensus_timestamp"),
                            EntityIdEndec.decode(rs.getLong("payer_account_id"), TOKEN),
                            receiver,
                            sender);
                    return sharedTransfer;
                });
    }

    /**
     * Ensure entity tables match expected state before V_1_47.0
     */
    private void revertToPreV_1_47() {
        // drop payer_account_id columns
        jdbcOperations
                .execute("alter table if exists assessed_custom_fee\n" +
                        "    drop column if exists payer_account_id;");

        jdbcOperations
                .execute("alter table if exists crypto_transfer\n" +
                        "    drop column if exists payer_account_id;");

        jdbcOperations
                .execute("alter table if exists nft_transfer\n" +
                        "    drop column if exists payer_account_id;");

        jdbcOperations
                .execute("alter table if exists non_fee_transfer\n" +
                        "    drop column if exists payer_account_id;");

        jdbcOperations
                .execute("alter table if exists token_transfer\n" +
                        "    drop column if exists payer_account_id;");
    }

    private String longListSerializer(List<Long> longs) {
        return longs == null ? "" : "{" + StringUtils.join(longs, ",") + "}";
    }

    // custom class with shared attributes for all transfer classes prior to migration
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private class SharedTransfer {
        private long amount;
        private long consensusTimeStamp;
        private EntityId payerAccountId;
        private EntityId receiver;
        private EntityId sender;
    }
}
