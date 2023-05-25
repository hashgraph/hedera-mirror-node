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

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
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
    private final NftTransferRepository nftTransferRepository;
    private final TokenTransferRepository tokenTransferRepository;

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isOne();
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(cryptoTransferRepository.findAll()).isEmpty();
        assertThat(nftTransferRepository.findAll()).isEmpty();
        assertThat(tokenTransferRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var contractResultSenderId = EntityId.of("0.0.2119901", CONTRACT);
        var keyUnaffectedEntity = persistEntity(contractResultSenderId.getEntityNum());
        var contractResultSenderId2 = EntityId.of("0.0.2119902", CONTRACT);
        var keyAffectedEntity = persistEntity(contractResultSenderId2.getEntityNum());
        var priorContractResultSenderId = EntityId.of("0.0.2119900", CONTRACT);
        var priorEntity = persistEntity(priorContractResultSenderId.getEntityNum());
        var noKeyEntity = persistEntity(null);
        var unaffectedEntity = persistEntity(null);

        var cryptoTransfersPair = setupCryptoTransfers(
                contractResultSenderId,
                contractResultSenderId2,
                priorContractResultSenderId,
                keyUnaffectedEntity,
                keyAffectedEntity,
                priorEntity,
                noKeyEntity,
                unaffectedEntity);

        var nftTransfersPair = setupNftTransfers(
                contractResultSenderId,
                contractResultSenderId2,
                priorContractResultSenderId,
                keyUnaffectedEntity,
                keyAffectedEntity,
                priorEntity,
                noKeyEntity,
                unaffectedEntity);

        var tokenTransfersPair = setupTokenTransfers(
                contractResultSenderId,
                contractResultSenderId2,
                priorContractResultSenderId,
                keyUnaffectedEntity,
                keyAffectedEntity,
                priorEntity,
                noKeyEntity,
                unaffectedEntity);

        // when
        migration.doMigrate();

        // then
        var expectedCryptoTransfers = new ArrayList<>(cryptoTransfersPair.getLeft());
        expectedCryptoTransfers.forEach(t -> t.setIsApproval(true));
        expectedCryptoTransfers.addAll(cryptoTransfersPair.getRight());
        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedCryptoTransfers);

        var expectedNftTransfers = new ArrayList<>(nftTransfersPair.getLeft());
        expectedNftTransfers.forEach(t -> t.setIsApproval(true));
        expectedNftTransfers.addAll(nftTransfersPair.getRight());
        assertThat(nftTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedNftTransfers);

        var expectedTokenTransfers = new ArrayList<>(tokenTransfersPair.getLeft());
        expectedTokenTransfers.forEach(t -> t.setIsApproval(true));
        expectedTokenTransfers.addAll(tokenTransfersPair.getRight());
        assertThat(tokenTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenTransfers);
    }

    @Test
    void repeatableMigration() {
        // given
        migrate();
        var firstCryptoTransfers = cryptoTransferRepository.findAll();
        var firstPassNftTransfers = nftTransferRepository.findAll();
        var firstPassTokenTransfers = tokenTransferRepository.findAll();

        // when
        migration.doMigrate();

        var secondPassCryptoTransfers = cryptoTransferRepository.findAll();
        var secondPassNftTransfers = nftTransferRepository.findAll();
        var secondPassTokenTransfers = tokenTransferRepository.findAll();

        // then
        assertThat(firstCryptoTransfers).containsExactlyInAnyOrderElementsOf(secondPassCryptoTransfers);
        assertThat(firstPassNftTransfers).containsExactlyInAnyOrderElementsOf(secondPassNftTransfers);
        assertThat(firstPassTokenTransfers).containsExactlyInAnyOrderElementsOf(secondPassTokenTransfers);
    }

    private Pair<List<CryptoTransfer>, List<CryptoTransfer>> setupCryptoTransfers(
            EntityId contractResultSenderId,
            EntityId contractResultSenderId2,
            EntityId priorContractResultSenderId,
            Entity keyUnaffectedEntity,
            Entity keyAffectedEntity,
            Entity priorEntity,
            Entity noKeyEntity,
            Entity unaffectedEntity) {
        var approvalTrueCryptoTransfers = new ArrayList<CryptoTransfer>();
        var approvalFalseCryptoTransfers = new ArrayList<CryptoTransfer>();

        // crypto transfer with threshold key matching the contract result sender id should not have isApproval set to
        // true
        var cryptoMatchingThreshold = persistCryptoTransfer(keyUnaffectedEntity.getId(), contractResultSenderId);
        // corresponding contract result for the synthetic crypto transfer
        persistContractResult(contractResultSenderId, cryptoMatchingThreshold.getConsensusTimestamp());
        approvalFalseCryptoTransfers.add(cryptoMatchingThreshold);

        // crypto transfer with threshold key not matching the contract result sender id should have isApproval set to
        // true
        var cryptoNotMatchingThreshold = persistCryptoTransfer(keyAffectedEntity.getId(), contractResultSenderId2);
        persistContractResult(contractResultSenderId, cryptoNotMatchingThreshold.getConsensusTimestamp());
        approvalTrueCryptoTransfers.add(cryptoNotMatchingThreshold);

        // crypto transfer with threshold key matching the contract result sender prior grandfathered id, isApproval
        // should not be affected
        var priorTransferThreshold = persistCryptoTransfer(priorEntity.getId(), priorContractResultSenderId);
        persistContractResult(priorContractResultSenderId, priorTransferThreshold.getConsensusTimestamp());
        approvalFalseCryptoTransfers.add(priorTransferThreshold);

        // crypto transfer with no threshold key and transfer not sent to itself should have isApproval set to true
        var noKeyCryptoTransfer = persistCryptoTransfer(noKeyEntity.getId(), noKeyEntity.toEntityId());
        persistContractResult(contractResultSenderId, noKeyCryptoTransfer.getConsensusTimestamp());
        approvalTrueCryptoTransfers.add(noKeyCryptoTransfer);

        // crypto transfer with no threshold key and transfer sent to itself should not have isApproval set to true
        var unaffectedCryptoTransfer = persistCryptoTransfer(unaffectedEntity.getId(), unaffectedEntity.toEntityId());
        persistContractResult(unaffectedEntity.toEntityId(), unaffectedCryptoTransfer.getConsensusTimestamp());
        approvalFalseCryptoTransfers.add(unaffectedCryptoTransfer);

        // crypto transfer without a contract result, will not be affected by the migration
        approvalFalseCryptoTransfers.add(domainBuilder.cryptoTransfer().persist());

        // crypto transfer with the problem already fixed, it should not be affected by the migration
        var cryptoNotMatchingThresholdFixed =
                persistFixedCryptoTransfer(keyAffectedEntity.getId(), contractResultSenderId2);
        persistContractResult(contractResultSenderId, cryptoNotMatchingThresholdFixed.getConsensusTimestamp());
        approvalFalseCryptoTransfers.add(cryptoNotMatchingThresholdFixed);

        return Pair.of(approvalTrueCryptoTransfers, approvalFalseCryptoTransfers);
    }

    private Pair<List<NftTransfer>, List<NftTransfer>> setupNftTransfers(
            EntityId contractResultSenderId,
            EntityId contractResultSenderId2,
            EntityId priorContractResultSenderId,
            Entity keyUnaffectedEntity,
            Entity keyAffectedEntity,
            Entity priorEntity,
            Entity noKeyEntity,
            Entity unaffectedEntity) {
        var approvalTrueNftTransfers = new ArrayList<NftTransfer>();
        var approvalFalseNftTransfers = new ArrayList<NftTransfer>();

        // nft transfer with threshold key matching the contract result sender id should not have isApproval set to true
        var nftMatchingThreshold = persistNftTransfer(keyUnaffectedEntity.toEntityId(), contractResultSenderId);
        persistContractResult(
                contractResultSenderId, nftMatchingThreshold.getId().getConsensusTimestamp());
        approvalFalseNftTransfers.add(nftMatchingThreshold);

        // nft transfer with threshold key not matching the contract result sender id should have isApproval set to true
        var nftNotMatchingThreshold = persistNftTransfer(keyAffectedEntity.toEntityId(), contractResultSenderId2);
        persistContractResult(
                contractResultSenderId, nftNotMatchingThreshold.getId().getConsensusTimestamp());
        approvalTrueNftTransfers.add(nftNotMatchingThreshold);

        // nft transfer with threshold key matching the contract result sender prior grandfathered id, isApproval should
        // not be affected
        var priorNftTransferThreshold = persistNftTransfer(priorEntity.toEntityId(), priorContractResultSenderId);
        persistContractResult(
                priorContractResultSenderId, priorNftTransferThreshold.getId().getConsensusTimestamp());
        approvalFalseNftTransfers.add(priorNftTransferThreshold);

        // nft transfer with no threshold key and transfer not sent to itself should have isApproval set to true
        var noKeyNftTransfer = persistNftTransfer(noKeyEntity.toEntityId(), noKeyEntity.toEntityId());
        persistContractResult(contractResultSenderId, noKeyNftTransfer.getId().getConsensusTimestamp());
        approvalTrueNftTransfers.add(noKeyNftTransfer);

        // nft transfer with no threshold key and transfer sent to itself should not have isApproval set to true
        var unaffectedNftTransfer = persistNftTransfer(unaffectedEntity.toEntityId(), unaffectedEntity.toEntityId());
        persistContractResult(
                unaffectedEntity.toEntityId(), unaffectedNftTransfer.getId().getConsensusTimestamp());
        approvalFalseNftTransfers.add(unaffectedNftTransfer);

        // nft transfer without a contract result, will not be affected by the migration
        approvalFalseNftTransfers.add(domainBuilder.nftTransfer().persist());

        // nft transfer with the problem already fixed, it should not be affected by the migration
        var nftNotMatchingThresholdFixed =
                persistFixedNftTransfer(keyAffectedEntity.toEntityId(), contractResultSenderId2);
        persistContractResult(
                contractResultSenderId, nftNotMatchingThresholdFixed.getId().getConsensusTimestamp());
        approvalFalseNftTransfers.add(nftNotMatchingThresholdFixed);

        return Pair.of(approvalTrueNftTransfers, approvalFalseNftTransfers);
    }

    private Pair<List<TokenTransfer>, List<TokenTransfer>> setupTokenTransfers(
            EntityId contractResultSenderId,
            EntityId contractResultSenderId2,
            EntityId priorContractResultSenderId,
            Entity keyUnaffectedEntity,
            Entity keyAffectedEntity,
            Entity priorEntity,
            Entity noKeyEntity,
            Entity unaffectedEntity) {
        var approvalTrueTokenTransfers = new ArrayList<TokenTransfer>();
        var approvalFalseTokenTransfers = new ArrayList<TokenTransfer>();

        // token transfer with threshold key matching the contract result sender id should not have isApproval set to
        // true
        var tokenMatchingThreshold = persistTokenTransfer(keyUnaffectedEntity.toEntityId(), contractResultSenderId);
        persistContractResult(
                contractResultSenderId, tokenMatchingThreshold.getId().getConsensusTimestamp());
        approvalFalseTokenTransfers.add(tokenMatchingThreshold);

        // token transfer with threshold key not matching the contract result sender id should have isApproval set to
        // true
        var tokenNotMatchingThreshold = persistTokenTransfer(keyAffectedEntity.toEntityId(), contractResultSenderId2);
        persistContractResult(
                contractResultSenderId, tokenNotMatchingThreshold.getId().getConsensusTimestamp());
        approvalTrueTokenTransfers.add(tokenNotMatchingThreshold);

        // token transfer with threshold key matching the contract result sender prior grandfathered id, isApproval
        // should not be affected
        var priorTokenTransferThreshold = persistTokenTransfer(priorEntity.toEntityId(), priorContractResultSenderId);
        persistContractResult(
                priorContractResultSenderId, priorTokenTransferThreshold.getId().getConsensusTimestamp());
        approvalFalseTokenTransfers.add(priorTokenTransferThreshold);

        // token transfer with no threshold key and transfer not sent to itself should have isApproval set to true
        var noKeyTokenTransfer = persistTokenTransfer(noKeyEntity.toEntityId(), noKeyEntity.toEntityId());
        persistContractResult(contractResultSenderId, noKeyTokenTransfer.getId().getConsensusTimestamp());
        approvalTrueTokenTransfers.add(noKeyTokenTransfer);

        // token transfer with no threshold key and transfer sent to itself should not have isApproval set to true
        var unaffectedTokenTransfer =
                persistTokenTransfer(unaffectedEntity.toEntityId(), unaffectedEntity.toEntityId());
        persistContractResult(
                unaffectedEntity.toEntityId(), unaffectedTokenTransfer.getId().getConsensusTimestamp());
        approvalFalseTokenTransfers.add(unaffectedTokenTransfer);

        // nft transfer without a contract result, will not be affected by the migration
        approvalFalseTokenTransfers.add(domainBuilder.tokenTransfer().persist());

        // token transfer with the problem already fixed, it should not be affected by the migration
        var tokenNotMatchingThresholdFixed =
                persistFixedTokenTransfer(keyAffectedEntity.toEntityId(), contractResultSenderId2);
        persistContractResult(
                contractResultSenderId, tokenNotMatchingThresholdFixed.getId().getConsensusTimestamp());
        approvalFalseTokenTransfers.add(tokenNotMatchingThresholdFixed);

        return Pair.of(approvalTrueTokenTransfers, approvalFalseTokenTransfers);
    }

    private byte[] getThresholdKey(long contractNum) {
        var thresholdKeyList = KeyList.newBuilder()
                .addKeys(Key.newBuilder().setEd25519(ByteString.EMPTY).build())
                .addKeys(Key.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(contractNum))
                        .build())
                .build();
        var thresholdKey = ThresholdKey.newBuilder().setKeys(thresholdKeyList).build();
        return Key.newBuilder().setThresholdKey(thresholdKey).build().toByteArray();
    }

    private void persistContractResult(EntityId senderId, long consensusTimestamp) {
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(senderId).consensusTimestamp(consensusTimestamp))
                .persist();
    }

    private CryptoTransfer persistCryptoTransfer(long entityId, EntityId payerAccountId) {
        return domainBuilder
                .cryptoTransfer()
                .customize(t -> t.entityId(entityId).isApproval(false).payerAccountId(payerAccountId))
                .persist();
    }

    private CryptoTransfer persistFixedCryptoTransfer(long entityId, EntityId payerAccountId) {
        return domainBuilder
                .cryptoTransfer()
                .customize(t -> t.entityId(entityId).isApproval(true).payerAccountId(payerAccountId))
                .persist();
    }

    private Entity persistEntity(Long contractNum) {
        var entity = domainBuilder.entity();
        if (contractNum != null) {
            entity.customize(e -> e.key(getThresholdKey(contractNum)));
        }
        return entity.persist();
    }

    private NftTransfer persistNftTransfer(EntityId entityId, EntityId payerAccountId) {
        return domainBuilder
                .nftTransfer()
                .customize(t -> t.receiverAccountId(entityId)
                        .payerAccountId(payerAccountId)
                        .isApproval(false))
                .persist();
    }

    private NftTransfer persistFixedNftTransfer(EntityId entityId, EntityId payerAccountId) {
        return domainBuilder
                .nftTransfer()
                .customize(t -> t.receiverAccountId(entityId)
                        .payerAccountId(payerAccountId)
                        .isApproval(true))
                .persist();
    }

    private TokenTransfer persistTokenTransfer(EntityId entityId, EntityId payerAccountId) {
        var id = TokenTransfer.Id.builder()
                .accountId(entityId)
                .consensusTimestamp(domainBuilder.timestamp())
                .tokenId(domainBuilder.entityId(TOKEN))
                .build();
        return domainBuilder
                .tokenTransfer()
                .customize(t -> t.id(id).payerAccountId(payerAccountId).isApproval(false))
                .persist();
    }

    private TokenTransfer persistFixedTokenTransfer(EntityId entityId, EntityId payerAccountId) {
        var id = TokenTransfer.Id.builder()
                .accountId(entityId)
                .consensusTimestamp(domainBuilder.timestamp())
                .tokenId(domainBuilder.entityId(TOKEN))
                .build();
        return domainBuilder
                .tokenTransfer()
                .customize(t -> t.id(id).payerAccountId(payerAccountId).isApproval(true))
                .persist();
    }
}
