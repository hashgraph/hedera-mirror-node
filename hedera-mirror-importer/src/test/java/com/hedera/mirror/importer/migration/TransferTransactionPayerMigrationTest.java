/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.NftTransferId;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.DisableRepeatableSqlMigration;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.repository.NonFeeTransferRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.46.6")
class TransferTransactionPayerMigrationTest extends IntegrationTest {

    private static final EntityId NODE_ACCOUNT_ID = EntityId.of(0, 0, 3, EntityType.ACCOUNT);
    private static final EntityId PAYER_ID = EntityId.of(0, 0, 10001, EntityType.ACCOUNT);

    private final @Owner JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.47.0__add_transfer_payer.sql")
    private final File migrationSql;

    private final EntityRepository entityRepository;
    private final TransactionRepository transactionRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final NftTransferRepository nftTransferRepository;
    private final NonFeeTransferRepository nonFeeTransferRepository;
    private final TokenTransferRepository tokenTransferRepository;

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
        Entity node = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(10L).timestampRange(Range.atLeast(10L)))
                .get();
        Entity treasury = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(20L).timestampRange(Range.atLeast(20L)))
                .get();
        Entity sender = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(30L).timestampRange(Range.atLeast(30L)))
                .get();
        Entity receiver = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(40L).timestampRange(Range.atLeast(40L)))
                .get();
        Entity contract = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(50L)
                        .timestampRange(Range.atLeast(50L))
                        .type(EntityType.CONTRACT))
                .get();
        Entity file = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(60L)
                        .timestampRange(Range.atLeast(60L))
                        .type(EntityType.FILE))
                .get();
        Entity topic = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(70L)
                        .timestampRange(Range.atLeast(70L))
                        .type(EntityType.TOPIC))
                .get();
        Entity token = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(80L)
                        .timestampRange(Range.atLeast(80L))
                        .type(TOKEN))
                .get();
        Entity schedule = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(90L)
                        .timestampRange(Range.atLeast(90L))
                        .type(EntityType.SCHEDULE))
                .get();

        // given
        persistEntities(List.of(node, treasury, sender, receiver, contract, file, topic, token, schedule));

        MigrationTransaction transfer1 =
                transaction(schedule.getCreatedTimestamp() + 200L, 0, SUCCESS, TransactionType.CRYPTOTRANSFER);
        MigrationTransaction transfer2 =
                transaction(schedule.getCreatedTimestamp() + 300L, 0, SUCCESS, TransactionType.CRYPTOTRANSFER);
        MigrationTransaction transfer3 =
                transaction(schedule.getCreatedTimestamp() + 400L, 0, SUCCESS, TransactionType.CRYPTOTRANSFER);
        MigrationTransaction transfer4 =
                transaction(schedule.getCreatedTimestamp() + 500L, 0, SUCCESS, TransactionType.CRYPTOTRANSFER);
        MigrationTransaction transfer5 =
                transaction(schedule.getCreatedTimestamp() + 600L, 0, SUCCESS, TransactionType.CRYPTOTRANSFER);
        persistTransactions(List.of(
                transaction(
                        contract.getCreatedTimestamp(),
                        contract.getId(),
                        SUCCESS,
                        TransactionType.CONTRACTCREATEINSTANCE),
                transaction(
                        contract.getCreatedTimestamp() + 1,
                        contract.getId(),
                        INSUFFICIENT_TX_FEE,
                        TransactionType.CONTRACTUPDATEINSTANCE),
                transaction(file.getCreatedTimestamp(), file.getId(), SUCCESS, TransactionType.FILECREATE),
                transaction(file.getCreatedTimestamp() + 1, file.getId(), SUCCESS, TransactionType.FILEDELETE),
                transaction(topic.getCreatedTimestamp(), topic.getId(), SUCCESS, TransactionType.CONSENSUSCREATETOPIC),
                transaction(
                        topic.getCreatedTimestamp() + 1, topic.getId(), SUCCESS, TransactionType.CONSENSUSUPDATETOPIC),
                transaction(
                        topic.getCreatedTimestamp() + 2, topic.getId(), SUCCESS, TransactionType.CONSENSUSUPDATETOPIC),
                transaction(
                        topic.getCreatedTimestamp() + 3, topic.getId(), SUCCESS, TransactionType.CONSENSUSUPDATETOPIC),
                transaction(token.getCreatedTimestamp(), token.getId(), SUCCESS, TransactionType.TOKENCREATION),
                transaction(schedule.getCreatedTimestamp(), schedule.getId(), SUCCESS, TransactionType.SCHEDULECREATE),
                transfer1,
                transfer2,
                transfer3,
                transfer4,
                transfer5));

        EntityId nodeId = node.toEntityId();
        EntityId treasuryId = treasury.toEntityId();
        EntityId senderId = sender.toEntityId();
        EntityId receiverId = receiver.toEntityId();
        EntityId tokenId = token.toEntityId();
        long senderPaymentAmount = -45L;
        long receivedAmount = 30L;
        long nodePaymentAmount = 10L;
        long treasuryPaymentAmount = 5L;

        AssessedCustomFee assessedCustomFee1 = new AssessedCustomFee();
        assessedCustomFee1.setAmount(receivedAmount);
        assessedCustomFee1.setEffectivePayerAccountIds(List.of(senderId.getId()));
        assessedCustomFee1.setId(new AssessedCustomFee.Id(receiverId, transfer1.getConsensusTimestamp()));
        assessedCustomFee1.setPayerAccountId(tokenId);
        assessedCustomFee1.setTokenId(tokenId);

        AssessedCustomFee assessedCustomFee2 = new AssessedCustomFee();
        assessedCustomFee2.setAmount(receivedAmount);
        assessedCustomFee2.setEffectivePayerAccountIds(List.of(senderId.getId()));
        assessedCustomFee2.setId(new AssessedCustomFee.Id(receiverId, transfer5.getConsensusTimestamp()));
        assessedCustomFee2.setPayerAccountId(tokenId);
        assessedCustomFee2.setTokenId(tokenId);

        persistAssessedCustomFees(List.of(
                // assessed custom fee transfer
                assessedCustomFee1,
                // all transfers
                assessedCustomFee2));

        var t1c = transfer1.getConsensusTimestamp();
        var t2c = transfer2.getConsensusTimestamp();
        var t3c = transfer3.getConsensusTimestamp();
        var t4c = transfer4.getConsensusTimestamp();
        var t5c = transfer5.getConsensusTimestamp();
        var senderCryptoTransferBuilder =
                CryptoTransfer.builder().amount(senderPaymentAmount).entityId(senderId.getId());
        var receivedCryptoTransferBuilder =
                CryptoTransfer.builder().amount(receivedAmount).entityId(receiverId.getId());
        var nodeCryptoTransferBuilder =
                CryptoTransfer.builder().amount(nodePaymentAmount).entityId(nodeId.getId());
        var treasuryCryptoTransferBuilder =
                CryptoTransfer.builder().amount(treasuryPaymentAmount).entityId(treasuryId.getId());
        persistCryptoTransfers(List.of(
                // assessed custom fee transfer
                senderCryptoTransferBuilder.consensusTimestamp(t1c).build(),
                receivedCryptoTransferBuilder.consensusTimestamp(t1c).build(),
                nodeCryptoTransferBuilder.consensusTimestamp(t1c).build(),
                treasuryCryptoTransferBuilder.consensusTimestamp(t1c).build(),
                // crypto only transfer
                senderCryptoTransferBuilder.consensusTimestamp(t2c).build(),
                receivedCryptoTransferBuilder.consensusTimestamp(t2c).build(),
                nodeCryptoTransferBuilder.consensusTimestamp(t2c).build(),
                treasuryCryptoTransferBuilder.consensusTimestamp(t2c).build(),
                // nft transfer
                senderCryptoTransferBuilder.consensusTimestamp(t3c).build(),
                receivedCryptoTransferBuilder.consensusTimestamp(t3c).build(),
                nodeCryptoTransferBuilder.consensusTimestamp(t3c).build(),
                treasuryCryptoTransferBuilder.consensusTimestamp(t3c).build(),
                // token transfer
                senderCryptoTransferBuilder.consensusTimestamp(t4c).build(),
                receivedCryptoTransferBuilder.consensusTimestamp(t4c).build(),
                nodeCryptoTransferBuilder.consensusTimestamp(t4c).build(),
                treasuryCryptoTransferBuilder.consensusTimestamp(t4c).build(),
                // all transfers
                senderCryptoTransferBuilder.consensusTimestamp(t5c).build(),
                receivedCryptoTransferBuilder.consensusTimestamp(t5c).build(),
                nodeCryptoTransferBuilder.consensusTimestamp(t5c).build(),
                treasuryCryptoTransferBuilder.consensusTimestamp(t5c).build()));

        persistNonFeeTransfers(List.of(
                // assessed custom fee only transfer
                domainBuilder
                        .nonFeeTransfer()
                        .customize(n -> n.consensusTimestamp(transfer1.getConsensusTimestamp())
                                .amount(senderPaymentAmount)
                                .entityId(senderId))
                        .get(),
                domainBuilder
                        .nonFeeTransfer()
                        .customize(n -> n.amount(receivedAmount)
                                .consensusTimestamp(transfer1.getConsensusTimestamp())
                                .entityId(receiverId))
                        .get(),
                // crypto only transfer
                domainBuilder
                        .nonFeeTransfer()
                        .customize(n -> n.amount(senderPaymentAmount)
                                .consensusTimestamp(transfer2.getConsensusTimestamp())
                                .entityId(senderId))
                        .get(),
                domainBuilder
                        .nonFeeTransfer()
                        .customize(n -> n.amount(receivedAmount)
                                .consensusTimestamp(transfer2.getConsensusTimestamp())
                                .entityId(receiverId))
                        .get(),
                // nft transfer
                domainBuilder
                        .nonFeeTransfer()
                        .customize(n -> n.amount(senderPaymentAmount)
                                .consensusTimestamp(transfer3.getConsensusTimestamp())
                                .entityId(senderId))
                        .get(),
                domainBuilder
                        .nonFeeTransfer()
                        .customize(n -> n.amount(receivedAmount)
                                .consensusTimestamp(transfer3.getConsensusTimestamp())
                                .entityId(receiverId))
                        .get(),
                // token transfer
                domainBuilder
                        .nonFeeTransfer()
                        .customize(n -> n.amount(senderPaymentAmount)
                                .consensusTimestamp(transfer4.getConsensusTimestamp())
                                .entityId(senderId))
                        .get(),
                domainBuilder
                        .nonFeeTransfer()
                        .customize(n -> n.amount(receivedAmount)
                                .consensusTimestamp(transfer4.getConsensusTimestamp())
                                .entityId(receiverId))
                        .get(),
                // all transfers
                domainBuilder
                        .nonFeeTransfer()
                        .customize(n -> n.amount(senderPaymentAmount)
                                .consensusTimestamp(transfer5.getConsensusTimestamp())
                                .entityId(senderId))
                        .get(),
                domainBuilder
                        .nonFeeTransfer()
                        .customize(n -> n.amount(receivedAmount)
                                .consensusTimestamp(transfer5.getConsensusTimestamp())
                                .entityId(receiverId))
                        .get()));

        persistNftTransfers(List.of(
                // nft transfer
                domainBuilder
                        .nftTransfer()
                        .customize(n -> n.id(new NftTransferId(transfer3.getConsensusTimestamp(), 1L, tokenId))
                                .payerAccountId(null)
                                .receiverAccountId(receiverId)
                                .senderAccountId(senderId))
                        .get(),
                // all transfers
                domainBuilder
                        .nftTransfer()
                        .customize(n -> n.id(new NftTransferId(transfer5.getConsensusTimestamp(), 2L, tokenId))
                                .payerAccountId(null)
                                .receiverAccountId(receiverId)
                                .senderAccountId(senderId))
                        .get()));

        persistTokenTransfers(List.of(
                // token transfer
                new TokenTransfer(transfer4.getConsensusTimestamp(), -receivedAmount, tokenId, senderId),
                new TokenTransfer(transfer4.getConsensusTimestamp(), receivedAmount, tokenId, receiverId),
                // all transfers
                new TokenTransfer(transfer5.getConsensusTimestamp(), -receivedAmount, tokenId, senderId),
                new TokenTransfer(transfer5.getConsensusTimestamp(), receivedAmount, tokenId, receiverId)));

        // when
        migrate();

        List<SharedTransfer> expectedAssessedCustomFeeTransfers = List.of(
                // assessed custom fee transfer
                new SharedTransfer(receivedAmount, transfer1.getConsensusTimestamp(), PAYER_ID, receiverId, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, senderId));

        List<SharedTransfer> expectedCryptoTransfers = List.of(
                // assessed custom fee transfer
                new SharedTransfer(senderPaymentAmount, transfer1.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer1.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer1.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(
                        treasuryPaymentAmount, transfer1.getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // crypto only transfer
                new SharedTransfer(senderPaymentAmount, transfer2.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer2.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer2.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(
                        treasuryPaymentAmount, transfer2.getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // nft transfer
                new SharedTransfer(senderPaymentAmount, transfer3.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer3.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer3.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(
                        treasuryPaymentAmount, transfer3.getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // token transfer
                new SharedTransfer(senderPaymentAmount, transfer4.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer4.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(
                        treasuryPaymentAmount, transfer4.getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // all transfers
                new SharedTransfer(senderPaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(
                        treasuryPaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, treasuryId, null));

        List<SharedTransfer> expectedNftTransfers = List.of(
                // nft transfer
                new SharedTransfer(1L, transfer3.getConsensusTimestamp(), PAYER_ID, receiverId, senderId),
                new SharedTransfer(2L, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, senderId));

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
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, null));

        List<SharedTransfer> expectedTokenTransfers = List.of(
                // token transfer
                new SharedTransfer(-receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // all transfer
                new SharedTransfer(-receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, null));

        // then
        assertThat(findAssessedCustomFees()).containsExactlyInAnyOrderElementsOf(expectedAssessedCustomFeeTransfers);

        assertThat(findCryptoTransfers()).containsExactlyInAnyOrderElementsOf(expectedCryptoTransfers);

        assertThat(findNftTransfers()).containsExactlyInAnyOrderElementsOf(expectedNftTransfers);

        assertThat(findNonFeeTransfersAsSharedTransfers()).containsExactlyInAnyOrderElementsOf(expectedNonFeeTransfers);

        assertThat(findTokenTransfers()).containsExactlyInAnyOrderElementsOf(expectedTokenTransfers);
    }

    private MigrationTransaction transaction(
            long consensusNs, long entityNum, ResponseCodeEnum result, TransactionType type) {
        MigrationTransaction transaction = new MigrationTransaction();
        transaction.setConsensusTimestamp(consensusNs);
        transaction.setEntityId(entityNum);
        transaction.setNodeAccountId(NODE_ACCOUNT_ID.getId());
        transaction.setPayerAccountId(PAYER_ID.getId());
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
                    "insert into assessed_custom_fee (amount, collector_account_id, consensus_timestamp, token_id,"
                            + "effective_payer_account_ids)"
                            + " values (?,?,?,?,?::bigint[])",
                    assessedCustomFee.getAmount(),
                    id.getCollectorAccountId().getId(),
                    id.getConsensusTimestamp(),
                    assessedCustomFee.getTokenId().getId(),
                    longListSerializer(assessedCustomFee.getEffectivePayerAccountIds()));
        }
    }

    private void persistCryptoTransfers(List<CryptoTransfer> cryptoTransfers) {
        for (CryptoTransfer cryptoTransfer : cryptoTransfers) {
            jdbcOperations.update(
                    "insert into crypto_transfer (amount, consensus_timestamp, entity_id)" + " values (?,?,?)",
                    cryptoTransfer.getAmount(),
                    cryptoTransfer.getConsensusTimestamp(),
                    cryptoTransfer.getEntityId());
        }
    }

    private void persistNftTransfers(List<NftTransfer> nftTransfers) {
        for (NftTransfer nftTransfer : nftTransfers) {
            var id = nftTransfer.getId();
            jdbcOperations.update(
                    "insert into nft_transfer (consensus_timestamp, receiver_account_id, sender_account_id, "
                            + "serial_number, token_id)"
                            + " values (?,?,?,?,?)",
                    id.getConsensusTimestamp(),
                    nftTransfer.getReceiverAccountId().getId(),
                    nftTransfer.getSenderAccountId().getId(),
                    id.getSerialNumber(),
                    id.getTokenId().getId());
        }
    }

    private void persistNonFeeTransfers(List<NonFeeTransfer> nonFeeTransfers) {
        for (NonFeeTransfer nonFeeTransfer : nonFeeTransfers) {
            var id = nonFeeTransfer.getId();
            jdbcOperations.update(
                    "insert into non_fee_transfer (amount, entity_id, consensus_timestamp)" + " values (?,?,?)",
                    nonFeeTransfer.getAmount(),
                    nonFeeTransfer.getEntityId().getId(),
                    nonFeeTransfer.getConsensusTimestamp());
        }
    }

    private void persistTokenTransfers(List<TokenTransfer> tokenTransfers) {
        for (TokenTransfer tokenTransfer : tokenTransfers) {
            var id = tokenTransfer.getId();
            jdbcOperations.update(
                    "insert into token_transfer (amount, account_id, consensus_timestamp, token_id)"
                            + " values (?,?,?,?)",
                    tokenTransfer.getAmount(),
                    id.getAccountId().getId(),
                    id.getConsensusTimestamp(),
                    id.getTokenId().getId());
        }
    }

    private List<SharedTransfer> findAssessedCustomFees() {
        return jdbcOperations.query("select * from assessed_custom_fee", (rs, rowNum) -> {
            List<Long> effectivePayers = Arrays.asList(
                    (Long[]) rs.getArray("effective_payer_account_ids").getArray());
            EntityId sender = ObjectUtils.isNotEmpty(effectivePayers)
                    ? EntityIdEndec.decode(effectivePayers.get(0), EntityType.ACCOUNT)
                    : null;
            EntityId receiver = EntityIdEndec.decode(rs.getLong("collector_account_id"), EntityType.ACCOUNT);
            SharedTransfer sharedTransfer = new SharedTransfer(
                    rs.getLong("amount"),
                    rs.getLong("consensus_timestamp"),
                    EntityIdEndec.decode(rs.getLong("payer_account_id"), EntityType.ACCOUNT),
                    receiver,
                    sender);
            return sharedTransfer;
        });
    }

    private List<SharedTransfer> findCryptoTransfers() {
        return jdbcOperations.query("select * from crypto_transfer", (rs, rowNum) -> {
            Long amount = rs.getLong("amount");
            EntityId sender = amount < 0 ? EntityIdEndec.decode(rs.getLong("entity_id"), EntityType.ACCOUNT) : null;
            EntityId receiver = amount > 0 ? EntityIdEndec.decode(rs.getLong("entity_id"), EntityType.ACCOUNT) : null;
            SharedTransfer sharedTransfer = new SharedTransfer(
                    amount,
                    rs.getLong("consensus_timestamp"),
                    EntityIdEndec.decode(rs.getLong("payer_account_id"), EntityType.ACCOUNT),
                    receiver,
                    sender);
            return sharedTransfer;
        });
    }

    private List<SharedTransfer> findNftTransfers() {
        return jdbcOperations.query("select * from nft_transfer", (rs, rowNum) -> {
            SharedTransfer sharedTransfer = new SharedTransfer(
                    rs.getLong("serial_number"),
                    rs.getLong("consensus_timestamp"),
                    EntityIdEndec.decode(rs.getLong("payer_account_id"), EntityType.ACCOUNT),
                    EntityIdEndec.decode(rs.getLong("receiver_account_id"), EntityType.ACCOUNT),
                    EntityIdEndec.decode(rs.getLong("sender_account_id"), EntityType.ACCOUNT));
            return sharedTransfer;
        });
    }

    private List<SharedTransfer> findNonFeeTransfersAsSharedTransfers() {
        return jdbcOperations.query("select * from non_fee_transfer", (rs, rowNum) -> {
            Long amount = rs.getLong("amount");
            EntityId sender = amount < 0 ? EntityIdEndec.decode(rs.getLong("entity_id"), EntityType.ACCOUNT) : null;
            EntityId receiver = amount > 0 ? EntityIdEndec.decode(rs.getLong("entity_id"), EntityType.ACCOUNT) : null;
            SharedTransfer sharedTransfer = new SharedTransfer(
                    amount,
                    rs.getLong("consensus_timestamp"),
                    EntityIdEndec.decode(rs.getLong("payer_account_id"), EntityType.ACCOUNT),
                    receiver,
                    sender);
            return sharedTransfer;
        });
    }

    private List<SharedTransfer> findTokenTransfers() {
        return jdbcOperations.query("select * from token_transfer", (rs, rowNum) -> {
            Long amount = rs.getLong("amount");
            EntityId sender = amount < 0 ? EntityIdEndec.decode(rs.getLong("account_id"), EntityType.ACCOUNT) : null;
            EntityId receiver = amount > 0 ? EntityIdEndec.decode(rs.getLong("account_id"), EntityType.ACCOUNT) : null;
            SharedTransfer sharedTransfer = new SharedTransfer(
                    amount,
                    rs.getLong("consensus_timestamp"),
                    EntityIdEndec.decode(rs.getLong("payer_account_id"), TOKEN),
                    receiver,
                    sender);
            return sharedTransfer;
        });
    }

    private void persistEntities(List<Entity> entities) {
        for (Entity entity : entities) {
            jdbcOperations.update(
                    "insert into entity (id, created_timestamp, num, realm, shard, type, timestamp_range) values (?,"
                            + "?,?,?,?,?,?::int8range)",
                    entity.getId(),
                    entity.getCreatedTimestamp(),
                    entity.getNum(),
                    entity.getRealm(),
                    entity.getShard(),
                    entity.getType().getId(),
                    PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()));
        }
    }

    private void persistTransactions(List<MigrationTransaction> transactions) {
        transactions.forEach(t -> {
            jdbcOperations.update(c -> {
                PreparedStatement preparedStatement = c.prepareStatement("insert into transaction "
                        + "(consensus_timestamp, entity_id, node_account_id, payer_account_id, result, type, "
                        + "valid_start_ns) "
                        + "values (?, ?, ?, ?, ?, ?, ?)");
                preparedStatement.setLong(1, t.getConsensusTimestamp());
                preparedStatement.setLong(2, t.getEntityId());
                preparedStatement.setLong(3, t.getNodeAccountId());
                preparedStatement.setLong(4, t.getPayerAccountId());
                preparedStatement.setInt(5, t.getResult());
                preparedStatement.setInt(6, t.getType());
                preparedStatement.setLong(7, t.getValidStartNs());
                return preparedStatement;
            });
        });
    }

    /**
     * Ensure entity tables match expected state before V_1_47.0
     */
    private void revertToPreV_1_47() {
        // drop payer_account_id columns
        jdbcOperations.execute(
                "alter table if exists assessed_custom_fee\n" + "    drop column if exists payer_account_id;");

        jdbcOperations.execute(
                "alter table if exists crypto_transfer\n" + "    drop column if exists payer_account_id;");

        jdbcOperations.execute("alter table if exists nft_transfer\n" + "    drop column if exists payer_account_id;");

        jdbcOperations.execute(
                "alter table if exists non_fee_transfer\n" + "    drop column if exists payer_account_id;");

        jdbcOperations.execute(
                "alter table if exists token_transfer\n" + "    drop column if exists payer_account_id;");
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

    @Data
    private class MigrationTransaction {
        private Long consensusTimestamp;
        private Long entityId;
        private Long nodeAccountId;
        private Long payerAccountId;
        private Integer result;
        private Integer type;
        private Long validStartNs;
    }
}
