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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.shaded.org.apache.commons.lang3.tuple.Pair;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class SyntheticCryptoTransferApprovalsMigrationTest extends IntegrationTest {

    private final SyntheticCryptoTransferApprovalMigration migration;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final TransactionRepository transactionRepository;
    private final TokenTransferRepository tokenTransferRepository;

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isOne();
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(cryptoTransferRepository.findAll()).isEmpty();
        assertThat(transactionRepository.findAll()).isEmpty();
        assertThat(tokenTransferRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var contractResultSenderId = EntityId.of("0.0.2119901", CONTRACT);
        var contractResultSenderId2 = EntityId.of("0.0.2119902", CONTRACT);

        var currentKeyUnaffectedEntity = entityCurrentKey(contractResultSenderId.getEntityNum());
        var currentKeyAffectedEntity = entityCurrentKey(contractResultSenderId2.getEntityNum());
        var outsideBoundsContractResultSenderId = EntityId.of("0.0.2119900", CONTRACT);
        var outsidePastBoundaryEntity = entityCurrentKey(outsideBoundsContractResultSenderId.getEntityNum());
        var noKeyEntity = entityWithNoKey();
        var unaffectedEntity = entityWithNoKey();
        var pastKeyUnaffectedEntity = entityPastKey(contractResultSenderId.getEntityNum());
        var pastKeyAffectedEntity = entityPastKey(contractResultSenderId2.getEntityNum());
        var futureUnaffectedEntity = domainBuilder
                .entity()
                .customize(e -> e.key(getThresholdKey(contractResultSenderId.getEntityNum()))
                        .timestampRange(Range.atLeast(Long.MAX_VALUE - 1000))
                        .build())
                .persist();
        var thresholdTwoKeyEntity = domainBuilder
                .entity()
                .customize(e -> e.key(getThresholdTwoKey(contractResultSenderId.getEntityNum()))
                        .build())
                .persist();

        var cryptoTransfersPair = setupCryptoTransfers(
                contractResultSenderId,
                contractResultSenderId2,
                outsideBoundsContractResultSenderId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                outsidePastBoundaryEntity,
                noKeyEntity,
                unaffectedEntity,
                pastKeyUnaffectedEntity,
                pastKeyAffectedEntity,
                futureUnaffectedEntity,
                thresholdTwoKeyEntity);

        var nftPastKeyUnaffectedEntity = entityPastKey(contractResultSenderId.getEntityNum());
        var nftPastKeyAffectedEntity = entityPastKey(contractResultSenderId2.getEntityNum());
        var nftTransfersTransactionPair = setupTransactionNfts(
                contractResultSenderId,
                contractResultSenderId2,
                outsideBoundsContractResultSenderId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                outsidePastBoundaryEntity,
                noKeyEntity,
                unaffectedEntity,
                nftPastKeyUnaffectedEntity,
                nftPastKeyAffectedEntity,
                futureUnaffectedEntity,
                thresholdTwoKeyEntity);

        var tokenPastKeyUnaffectedEntity = entityPastKey(contractResultSenderId.getEntityNum());
        var tokenPastKeyAffectedEntity = entityPastKey(contractResultSenderId2.getEntityNum());
        var tokenTransfersPair = setupTokenTransfers(
                contractResultSenderId,
                contractResultSenderId2,
                outsideBoundsContractResultSenderId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                outsidePastBoundaryEntity,
                noKeyEntity,
                unaffectedEntity,
                tokenPastKeyUnaffectedEntity,
                tokenPastKeyAffectedEntity,
                futureUnaffectedEntity,
                thresholdTwoKeyEntity);

        // when
        migration.doMigrate();

        // then
        var expectedCryptoTransfers = new ArrayList<>(cryptoTransfersPair.getLeft());
        expectedCryptoTransfers.forEach(t -> t.setIsApproval(true));
        expectedCryptoTransfers.addAll(cryptoTransfersPair.getRight());
        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedCryptoTransfers);

        var expectedTokenTransfers = new ArrayList<>(tokenTransfersPair.getLeft());
        expectedTokenTransfers.forEach(t -> t.setIsApproval(true));
        expectedTokenTransfers.addAll(tokenTransfersPair.getRight());
        assertThat(tokenTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenTransfers);

        var expectedNftTransfers = new ArrayList<>(nftTransfersTransactionPair.getLeft());
        expectedNftTransfers.forEach(t -> t.setIsApproval(true));
        expectedNftTransfers.addAll(nftTransfersTransactionPair.getRight());

        var repositoryNftTransfers = new ArrayList<NftTransfer>();
        transactionRepository.findAll().forEach(t -> repositoryNftTransfers.addAll(t.getNftTransfer()));
        assertThat(repositoryNftTransfers).containsExactlyInAnyOrderElementsOf(expectedNftTransfers);
    }

    @Test
    void repeatableMigration() {
        // given
        migrate();
        var firstPassCryptoTransfers = cryptoTransferRepository.findAll();
        var firstPassNftTransfers = transactionRepository.findAll();
        var firstPassTokenTransfers = tokenTransferRepository.findAll();

        // when
        migration.doMigrate();

        var secondPassCryptoTransfers = cryptoTransferRepository.findAll();
        var secondPassNftTransfers = transactionRepository.findAll();
        var secondPassTokenTransfers = tokenTransferRepository.findAll();

        // then
        assertThat(firstPassCryptoTransfers).containsExactlyInAnyOrderElementsOf(secondPassCryptoTransfers);
        assertThat(firstPassNftTransfers).containsExactlyInAnyOrderElementsOf(secondPassNftTransfers);
        assertThat(firstPassTokenTransfers).containsExactlyInAnyOrderElementsOf(secondPassTokenTransfers);
    }

    private Pair<List<CryptoTransfer>, List<CryptoTransfer>> setupCryptoTransfers(
            EntityId contractResultSenderId,
            EntityId contractResultSenderId2,
            EntityId outsideBoundsContractResultSenderId,
            Entity currentKeyUnaffectedEntity,
            Entity currentKeyAffectedEntity,
            Entity outsidePastBoundaryEntity,
            Entity noKeyEntity,
            Entity unaffectedEntity,
            Entity pastKeyUnaffectedEntity,
            Entity pastKeyAffectedEntity,
            Entity futureUnaffectedEntity,
            Entity thresholdTwoKeyEntity) {
        var approvalTrueCryptoTransfers = new ArrayList<CryptoTransfer>();
        var unaffectedCryptoTransfers = new ArrayList<CryptoTransfer>();

        // crypto transfer with current threshold key matching the contract result sender id should not have isApproval
        // set to true
        var cryptoMatchingThreshold =
                persistCryptoTransfer(currentKeyUnaffectedEntity.getId(), contractResultSenderId, null, false);
        // corresponding contract result for the synthetic crypto transfer
        persistContractResult(contractResultSenderId, cryptoMatchingThreshold.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(cryptoMatchingThreshold);

        // crypto transfer with past threshold key matching the contract result sender id should not have isApproval set
        // to true
        var pastCryptoMatchingThreshold = persistCryptoTransfer(
                pastKeyUnaffectedEntity.getId(),
                contractResultSenderId,
                pastKeyUnaffectedEntity.getTimestampLower(),
                false);
        // corresponding contract result for the synthetic crypto transfer
        persistContractResult(contractResultSenderId, pastCryptoMatchingThreshold.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(pastCryptoMatchingThreshold);

        // crypto transfer with current threshold key not matching the contract result sender id should have isApproval
        // set to true
        var cryptoNotMatchingThreshold =
                persistCryptoTransfer(currentKeyAffectedEntity.getId(), contractResultSenderId2, null, false);
        persistContractResult(contractResultSenderId, cryptoNotMatchingThreshold.getConsensusTimestamp());
        approvalTrueCryptoTransfers.add(cryptoNotMatchingThreshold);

        // crypto transfer with past threshold key not matching the contract result sender id should have isApproval set
        // to true
        var pastCryptoNotMatchingThreshold = persistCryptoTransfer(
                pastKeyAffectedEntity.getId(),
                contractResultSenderId2,
                pastKeyAffectedEntity.getTimestampLower(),
                false);
        persistContractResult(contractResultSenderId, pastCryptoNotMatchingThreshold.getConsensusTimestamp());
        approvalTrueCryptoTransfers.add(pastCryptoNotMatchingThreshold);

        // crypto transfer with threshold key matching the contract result sender prior grandfathered id, isApproval
        // should not be affected
        var priorTransferThreshold = persistCryptoTransfer(
                outsidePastBoundaryEntity.getId(), outsideBoundsContractResultSenderId, null, false);
        persistContractResult(outsideBoundsContractResultSenderId, priorTransferThreshold.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(priorTransferThreshold);

        // crypto transfer with no threshold key and transfer not sent to itself should have isApproval set to true
        var noKeyCryptoTransfer = persistCryptoTransfer(noKeyEntity.getId(), noKeyEntity.toEntityId(), null, false);
        persistContractResult(contractResultSenderId, noKeyCryptoTransfer.getConsensusTimestamp());
        approvalTrueCryptoTransfers.add(noKeyCryptoTransfer);

        // crypto transfer with no threshold key and transfer sent to itself should not have isApproval set to true
        var unaffectedCryptoTransfer =
                persistCryptoTransfer(unaffectedEntity.getId(), unaffectedEntity.toEntityId(), null, false);
        persistContractResult(unaffectedEntity.toEntityId(), unaffectedCryptoTransfer.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(unaffectedCryptoTransfer);

        // crypto transfer without a contract result, will not be affected by the migration
        unaffectedCryptoTransfers.add(domainBuilder.cryptoTransfer().persist());

        // crypto transfer with approved set to true, it should not be affected by the migration
        var cryptoNotMatchingThresholdApproved =
                persistCryptoTransfer(currentKeyAffectedEntity.getId(), contractResultSenderId2, null, true);
        persistContractResult(contractResultSenderId, cryptoNotMatchingThresholdApproved.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(cryptoNotMatchingThresholdApproved);

        // crypto transfer outside the future bounds should not be affected by the migration
        var futureTransfer = persistCryptoTransfer(
                futureUnaffectedEntity.getId(), contractResultSenderId2, Long.MAX_VALUE - 999, false);
        persistContractResult(contractResultSenderId, futureTransfer.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(futureTransfer);

        // crypto transfer with threshold set to 2. A threshold over 1 should have isApproval set to
        // true
        var thresholdTwoTransfer =
                persistCryptoTransfer(thresholdTwoKeyEntity.getId(), contractResultSenderId2, null, false);
        persistContractResult(contractResultSenderId, thresholdTwoTransfer.getConsensusTimestamp());
        approvalTrueCryptoTransfers.add(thresholdTwoTransfer);

        return Pair.of(approvalTrueCryptoTransfers, unaffectedCryptoTransfers);
    }

    private Pair<List<NftTransfer>, List<NftTransfer>> setupTransactionNfts(
            EntityId contractResultSenderId,
            EntityId contractResultSenderId2,
            EntityId outsideBoundsContractResultSenderId,
            Entity currentKeyUnaffectedEntity,
            Entity currentKeyAffectedEntity,
            Entity outsidePastBoundaryEntity,
            Entity noKeyEntity,
            Entity unaffectedEntity,
            Entity pastKeyUnaffectedEntity,
            Entity pastKeyAffectedEntity,
            Entity futureUnaffectedEntity,
            Entity thresholdTwoKeyEntity) {
        var approvalTrueNftTransfers = new ArrayList<NftTransfer>();
        var unaffectedNftTransfers = new ArrayList<NftTransfer>();

        long currentTimestamp = domainBuilder.timestamp();
        long pastTimestamp = pastKeyAffectedEntity.getTimestampLower() + 1;

        // nft transfer with current threshold key matching the contract result sender id should not have isApproval set
        // to true
        var currentKeyUnaffectedNft =
                getNftTransfer(currentKeyUnaffectedEntity.toEntityId(), contractResultSenderId, false);
        unaffectedNftTransfers.add(currentKeyUnaffectedNft);

        // nft transfer with past threshold key matching the contract result sender id should not have isApproval set to
        // true
        var pastKeyUnaffectedNft = getNftTransfer(pastKeyUnaffectedEntity.toEntityId(), contractResultSenderId, false);
        unaffectedNftTransfers.add(pastKeyUnaffectedNft);

        // nft transfer with current threshold key not matching the contract result sender id should have isApproval set
        // to true
        var currentKeyAffectedNft =
                getNftTransfer(currentKeyAffectedEntity.toEntityId(), contractResultSenderId2, false);
        approvalTrueNftTransfers.add(currentKeyAffectedNft);

        // nft transfer with past threshold key not matching the contract result sender id should have isApproval set to
        // true
        var pastKeyAffectedNft = getNftTransfer(pastKeyAffectedEntity.toEntityId(), contractResultSenderId2, false);
        approvalTrueNftTransfers.add(pastKeyAffectedNft);

        // nft transfer with no threshold key and transfer not sent to itself should have isApproval set to true
        var noKeyNft = getNftTransfer(noKeyEntity.toEntityId(), noKeyEntity.toEntityId(), false);
        approvalTrueNftTransfers.add(noKeyNft);

        // nft transfer without a contract result, will not be affected by the migration
        var noContractResultNft = domainBuilder.nftTransfer().get();
        unaffectedNftTransfers.add(noContractResultNft);

        // nft transfer with approved set to true, it should not be affected by the migration
        var approvalTrueNft = getNftTransfer(currentKeyAffectedEntity.toEntityId(), contractResultSenderId2, true);
        unaffectedNftTransfers.add(approvalTrueNft);

        // nft transfer with a threshold set to 2. A threshold over 1 should have isApproval set to true
        var thresholdTwoNft = getNftTransfer(thresholdTwoKeyEntity.toEntityId(), contractResultSenderId2, false);
        approvalTrueNftTransfers.add(thresholdTwoNft);

        persistTransaction(pastTimestamp, List.of(pastKeyAffectedNft, pastKeyUnaffectedNft));
        persistTransaction(
                currentTimestamp,
                List.of(
                        currentKeyUnaffectedNft,
                        currentKeyAffectedNft,
                        noKeyNft,
                        noContractResultNft,
                        approvalTrueNft,
                        thresholdTwoNft));

        persistContractResult(contractResultSenderId, currentTimestamp);
        persistContractResult(contractResultSenderId, pastTimestamp);

        // nft transfer with threshold key matching the contract result sender prior grandfathered id, isApproval should
        // not be affected
        var outsidePastBoundaryNft = List.of(
                getNftTransfer(outsidePastBoundaryEntity.toEntityId(), outsideBoundsContractResultSenderId, false));
        var priorNftTransferThreshold = persistTransaction(null, outsidePastBoundaryNft);
        persistContractResult(outsideBoundsContractResultSenderId, priorNftTransferThreshold.getConsensusTimestamp());
        unaffectedNftTransfers.addAll(outsidePastBoundaryNft);

        // nft transfer with no threshold key and transfer sent by sender id should not have isApproval set to true
        var unaffectedNft =
                List.of(getNftTransfer(unaffectedEntity.toEntityId(), unaffectedEntity.toEntityId(), false));
        var unaffectedNftTransfer = persistTransaction(null, unaffectedNft);
        persistContractResult(unaffectedEntity.toEntityId(), unaffectedNftTransfer.getConsensusTimestamp());
        unaffectedNftTransfers.addAll(unaffectedNft);

        // nft transfer outside the future bounds should not be affected by the migration
        var futureUnaffectedNft =
                List.of(getNftTransfer(futureUnaffectedEntity.toEntityId(), contractResultSenderId2, false));
        var nftFuture = persistTransaction(Long.MAX_VALUE - 998, futureUnaffectedNft);
        persistContractResult(contractResultSenderId, nftFuture.getConsensusTimestamp());
        unaffectedNftTransfers.addAll(futureUnaffectedNft);

        // transaction with a list of nft transfers, the first and third should be migrated and the second should not
        var listNft = List.of(
                getNftTransfer(currentKeyAffectedEntity.toEntityId(), contractResultSenderId2, false),
                getNftTransfer(currentKeyUnaffectedEntity.toEntityId(), contractResultSenderId, false),
                getNftTransfer(pastKeyAffectedEntity.toEntityId(), contractResultSenderId2, false));
        var nftsTransaction = persistTransaction(null, listNft);
        persistContractResult(contractResultSenderId, nftsTransaction.getConsensusTimestamp());
        approvalTrueNftTransfers.add(listNft.get(0));
        unaffectedNftTransfers.add(listNft.get(1));
        approvalTrueNftTransfers.add(listNft.get(2));

        return Pair.of(approvalTrueNftTransfers, unaffectedNftTransfers);
    }

    private Pair<List<TokenTransfer>, List<TokenTransfer>> setupTokenTransfers(
            EntityId contractResultSenderId,
            EntityId contractResultSenderId2,
            EntityId outsideBoundsContractResultSenderId,
            Entity currentKeyUnaffectedEntity,
            Entity currentKeyAffectedEntity,
            Entity outsidePastBoundaryEntity,
            Entity noKeyEntity,
            Entity unaffectedEntity,
            Entity pastKeyUnaffectedEntity,
            Entity pastKeyAffectedEntity,
            Entity futureUnaffectedEntity,
            Entity thresholdTwoKeyEntity) {
        var approvalTrueTokenTransfers = new ArrayList<TokenTransfer>();
        var unaffectedTokenTransfers = new ArrayList<TokenTransfer>();

        // token transfer with threshold key matching the contract result sender id should not have isApproval set to
        // true
        var tokenMatchingThreshold =
                persistTokenTransfer(currentKeyUnaffectedEntity.toEntityId(), contractResultSenderId, null, false);
        persistContractResult(
                contractResultSenderId, tokenMatchingThreshold.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(tokenMatchingThreshold);

        // token transfer with past threshold key matching the contract result sender id should not have isApproval set
        // to true
        var pastTokenMatchingThreshold = persistTokenTransfer(
                pastKeyUnaffectedEntity.toEntityId(),
                contractResultSenderId,
                pastKeyUnaffectedEntity.getTimestampLower(),
                false);
        persistContractResult(
                contractResultSenderId, pastTokenMatchingThreshold.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(pastTokenMatchingThreshold);

        // token transfer with threshold key not matching the contract result sender id should have isApproval set to
        // true
        var tokenNotMatchingThreshold =
                persistTokenTransfer(currentKeyAffectedEntity.toEntityId(), contractResultSenderId2, null, false);
        persistContractResult(
                contractResultSenderId, tokenNotMatchingThreshold.getId().getConsensusTimestamp());
        approvalTrueTokenTransfers.add(tokenNotMatchingThreshold);

        // token transfer with past threshold key not matching the contract result sender id should have isApproval set
        // to true
        var pastTokenNotMatchingThreshold = persistTokenTransfer(
                pastKeyAffectedEntity.toEntityId(),
                contractResultSenderId2,
                pastKeyAffectedEntity.getTimestampLower(),
                false);
        persistContractResult(
                contractResultSenderId, pastTokenNotMatchingThreshold.getId().getConsensusTimestamp());
        approvalTrueTokenTransfers.add(pastTokenNotMatchingThreshold);

        // token transfer with threshold key matching the contract result sender prior grandfathered id, isApproval
        // should not be affected
        var priorTokenTransferThreshold = persistTokenTransfer(
                outsidePastBoundaryEntity.toEntityId(), outsideBoundsContractResultSenderId, null, false);
        persistContractResult(
                outsideBoundsContractResultSenderId,
                priorTokenTransferThreshold.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(priorTokenTransferThreshold);

        // token transfer with no threshold key and transfer not sent to itself should have isApproval set to true
        var noKeyTokenTransfer = persistTokenTransfer(noKeyEntity.toEntityId(), noKeyEntity.toEntityId(), null, false);
        persistContractResult(contractResultSenderId, noKeyTokenTransfer.getId().getConsensusTimestamp());
        approvalTrueTokenTransfers.add(noKeyTokenTransfer);

        // token transfer with no threshold key and transfer sent to itself should not have isApproval set to true
        var unaffectedTokenTransfer =
                persistTokenTransfer(unaffectedEntity.toEntityId(), unaffectedEntity.toEntityId(), null, false);
        persistContractResult(
                unaffectedEntity.toEntityId(), unaffectedTokenTransfer.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(unaffectedTokenTransfer);

        // token transfer without a contract result, will not be affected by the migration
        unaffectedTokenTransfers.add(domainBuilder.tokenTransfer().persist());

        // token transfer with the problem already fixed, it should not be affected by the migration
        var tokenNotMatchingThresholdFixed =
                persistTokenTransfer(currentKeyAffectedEntity.toEntityId(), contractResultSenderId2, null, true);
        persistContractResult(
                contractResultSenderId, tokenNotMatchingThresholdFixed.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(tokenNotMatchingThresholdFixed);

        // token transfer outside the future bounds should not be affected by the migration
        var tokenFuture = persistTokenTransfer(
                futureUnaffectedEntity.toEntityId(), contractResultSenderId2, Long.MAX_VALUE - 997, false);
        persistContractResult(contractResultSenderId, tokenFuture.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(tokenFuture);

        // token transfer with threshold set to 2. A threshold over 1 should have isApproval set to true
        var thresholdTwoTransfer =
                persistTokenTransfer(thresholdTwoKeyEntity.toEntityId(), contractResultSenderId2, null, false);
        persistContractResult(
                contractResultSenderId, thresholdTwoTransfer.getId().getConsensusTimestamp());
        approvalTrueTokenTransfers.add(thresholdTwoTransfer);

        return Pair.of(approvalTrueTokenTransfers, unaffectedTokenTransfers);
    }

    private byte[] getThresholdKey(Long contractNum) {
        if (contractNum == null) {
            return null;
        }
        var thresholdKeyList = KeyList.newBuilder()
                .addKeys(Key.newBuilder().setEd25519(ByteString.EMPTY).build())
                .addKeys(Key.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(contractNum))
                        .build())
                .build();
        var thresholdKey = ThresholdKey.newBuilder().setKeys(thresholdKeyList).build();
        return Key.newBuilder().setThresholdKey(thresholdKey).build().toByteArray();
    }

    // Threshold key with threshold set to 2
    private byte[] getThresholdTwoKey(Long contractNum) {
        var thresholdKeyList = KeyList.newBuilder()
                .addKeys(Key.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(domainBuilder.id()))
                        .build())
                .addKeys(Key.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(contractNum))
                        .build())
                .build();
        var thresholdKey = ThresholdKey.newBuilder()
                .setKeys(thresholdKeyList)
                .setThreshold(2)
                .build();
        return Key.newBuilder().setThresholdKey(thresholdKey).build().toByteArray();
    }

    private void persistContractResult(EntityId senderId, long consensusTimestamp) {
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(senderId).consensusTimestamp(consensusTimestamp))
                .persist();
    }

    private CryptoTransfer persistCryptoTransfer(
            long entityId, EntityId payerAccountId, Long consensusTimestamp, boolean isApproval) {
        long consensus = consensusTimestamp == null ? domainBuilder.timestamp() : consensusTimestamp;
        return domainBuilder
                .cryptoTransfer()
                .customize(t -> t.amount(-10) // debit from account
                        .entityId(entityId)
                        .isApproval(isApproval)
                        .payerAccountId(payerAccountId)
                        .consensusTimestamp(consensus))
                .persist();
    }

    private Entity entityWithNoKey() {
        return persistEntity(null, null);
    }

    private Entity entityCurrentKey(Long contractNum) {
        return persistEntity(contractNum, null);
    }

    private Entity entityPastKey(Long contractNum) {
        return persistEntity(domainBuilder.id(), contractNum);
    }

    private Entity persistEntity(Long currentContractNum, Long pastContractNum) {
        var builder = domainBuilder.entity().customize(e -> e.key(getThresholdKey(currentContractNum)));
        var currentEntity = builder.persist();
        if (pastContractNum != null) {
            var range = currentEntity.getTimestampRange();
            long oneMinute = 60000000000L; // 1 minute in nanoseconds.
            var rangeUpdate1 = Range.closedOpen(range.lowerEndpoint() - oneMinute, range.lowerEndpoint() - 1);
            var update1 = builder.customize(
                            e -> e.key(getThresholdKey(pastContractNum)).timestampRange(rangeUpdate1))
                    .get();
            var pastEntityHistory = saveHistory(update1);
            var pastEntity = domainBuilder
                    .entity()
                    .customize(e -> e.id(pastEntityHistory.getId())
                            .key(pastEntityHistory.getKey())
                            .num(pastEntityHistory.getNum())
                            .timestampRange(pastEntityHistory.getTimestampRange()))
                    .get();

            return pastEntity;
        }

        return currentEntity;
    }

    private NftTransfer getNftTransfer(EntityId entityId, EntityId senderAccountId, boolean isApproval) {
        return domainBuilder
                .nftTransfer()
                .customize(t -> t.receiverAccountId(entityId)
                        .senderAccountId(senderAccountId)
                        .isApproval(isApproval))
                .get();
    }

    private Transaction persistTransaction(Long consensusTimestamp, List<NftTransfer> nftTransfers) {
        long consensus = consensusTimestamp == null ? domainBuilder.timestamp() : consensusTimestamp;
        return domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensus).nftTransfer(nftTransfers))
                .persist();
    }

    private TokenTransfer persistTokenTransfer(
            EntityId entityId, EntityId payerAccountId, Long consensusTimestamp, boolean isApproval) {
        long consensus = consensusTimestamp == null ? domainBuilder.timestamp() : consensusTimestamp;
        var id = TokenTransfer.Id.builder()
                .accountId(entityId)
                .consensusTimestamp(consensus)
                .tokenId(domainBuilder.entityId(TOKEN))
                .build();
        return domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-10) // debit from account
                        .id(id)
                        .payerAccountId(payerAccountId)
                        .isApproval(isApproval))
                .persist();
    }

    private EntityHistory saveHistory(Entity entity) {
        return domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId())
                        .key(entity.getKey())
                        .num(entity.getNum())
                        .timestampRange(entity.getTimestampRange()))
                .persist();
    }
}
