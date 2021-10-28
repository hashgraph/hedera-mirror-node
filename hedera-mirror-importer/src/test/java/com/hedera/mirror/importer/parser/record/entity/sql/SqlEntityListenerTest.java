package com.hedera.mirror.importer.parser.record.entity.sql;

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

import static com.hedera.mirror.importer.domain.EntityType.ACCOUNT;
import static com.hedera.mirror.importer.domain.EntityType.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.Contract;
import com.hedera.mirror.importer.domain.ContractLog;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.DomainBuilder;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityType;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftId;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NftTransferId;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenId;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenPauseStatusEnum;
import com.hedera.mirror.importer.domain.TokenSupplyTypeEnum;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TokenTypeEnum;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionResult;
import com.hedera.mirror.importer.domain.TransactionSignature;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.ContractLogRepository;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.repository.NonFeeTransferRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.ScheduleRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.repository.TransactionSignatureRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class SqlEntityListenerTest extends IntegrationTest {
    private static final String KEY = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    private static final String KEY2 = "0a3312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    private static final EntityId TRANSACTION_PAYER = EntityId.of("0.0.1000", ACCOUNT);

    private final DomainBuilder domainBuilder;
    private final TransactionRepository transactionRepository;
    private final EntityRepository entityRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final NonFeeTransferRepository nonFeeTransferRepository;
    private final ContractRepository contractRepository;
    private final ContractLogRepository contractLogRepository;
    private final ContractResultRepository contractResultRepository;
    private final LiveHashRepository liveHashRepository;
    private final NftRepository nftRepository;
    private final NftTransferRepository nftTransferRepository;
    private final FileDataRepository fileDataRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final RecordFileRepository recordFileRepository;
    private final TokenRepository tokenRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final ScheduleRepository scheduleRepository;
    private final TransactionSignatureRepository transactionSignatureRepository;
    private final SqlEntityListener sqlEntityListener;
    private final SqlProperties sqlProperties;
    private final TransactionTemplate transactionTemplate;
    private final String filename1 = "2019-08-30T18_10_00.419072Z.rcd";
    private final String filename2 = "2019-08-30T18_10_05.419072Z.rcd";
    private RecordFile recordFile1;
    private RecordFile recordFile2;

    private static Key keyFromString(String key) {
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build();
    }

    @BeforeEach
    final void beforeEach() {
        recordFile1 = recordFile(1L, filename1, UUID.randomUUID().toString(), 0L, "fileHash0");
        recordFile2 = recordFile(10L, filename2, UUID.randomUUID().toString(), 1L, "fileHash1");

        sqlEntityListener.onStart();
    }

    @Test
    void isEnabled() {
        sqlProperties.setEnabled(false);
        assertThat(sqlEntityListener.isEnabled()).isFalse();

        sqlProperties.setEnabled(true);
        assertThat(sqlEntityListener.isEnabled()).isTrue();
    }

    @Test
    void onContract() {
        // given
        Contract contract = domainBuilder.contract().customize(c -> c.parentId(null)).get();
        Entity entity = new Entity();
        entity.setId(contract.getId());
        entity.setNum(contract.getNum());
        entity.setRealm(contract.getRealm());
        entity.setShard(contract.getShard());
        entity.setModifiedTimestamp(contract.getModifiedTimestamp() + 10L);
        entity.setType(ACCOUNT);

        // when
        sqlEntityListener.onEntityId(entity.toEntityId()); // Removed after onContract
        sqlEntityListener.onContract(contract);
        sqlEntityListener.onEntity(entity); // Ignored
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(entityRepository.count()).isZero();
        assertThat(contractRepository.findAll()).containsExactly(contract);
    }

    @Test
    void onContractMergeSame() {
        // given
        Contract contract = domainBuilder.contract().customize(c -> c.parentId(null)).get();

        // when
        sqlEntityListener.onEntityId(contract.toEntityId());
        sqlEntityListener.onContract(contract);
        sqlEntityListener.onContract(contract);
        sqlEntityListener.onEntityId(contract.toEntityId());
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(entityRepository.count()).isZero();
        assertThat(contractRepository.findAll()).containsExactly(contract);
    }

    @Test
    void onContractMergeDifferent() {
        // given
        Contract contract = domainBuilder.contract().customize(c -> c.parentId(null)).get();
        Contract contractUpdated = domainBuilder.contract()
                .customize(c -> c.parentId(null).id(contract.getId()).num(contract.getNum()))
                .get();

        // when
        sqlEntityListener.onContract(contract);
        sqlEntityListener.onContract(contractUpdated);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(entityRepository.count()).isZero();
        assertThat(contractRepository.findAll()).containsExactly(contractUpdated);
    }

    @Test
    void onContractWithParent() {
        // given
        Contract parent = domainBuilder.contract().persist();
        Contract contract = EntityId.of(0L, 0L, 100L, EntityType.CONTRACT).toEntity();
        contract.setCreatedTimestamp(1L);
        contract.setDeleted(false);
        contract.setModifiedTimestamp(1L);
        contract.setParentId(parent.toEntityId());

        // when
        sqlEntityListener.onContract(contract);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(entityRepository.count()).isZero();
        assertThat(contractRepository.findAll())
                .hasSize(2)
                .filteredOn(c -> c.getId().equals(contract.getId()))
                .first()
                .returns(contract.getCreatedTimestamp(), Contract::getCreatedTimestamp)
                .returns(contract.getId(), Contract::getId)
                .returns(contract.getModifiedTimestamp(), Contract::getModifiedTimestamp)
                .returns(contract.getNum(), Contract::getNum)
                .returns(contract.getParentId(), Contract::getParentId)
                .usingRecursiveComparison()
                .ignoringFields("createdTimestamp", "id", "num", "parentId", "timestampRange")
                .isEqualTo(parent);
    }

    // Tests scenario when mirror node has partial data and is missing contract parent
    @Test
    void onContractWithMissingParent() {
        // given
        Contract contract = EntityId.of(0L, 0L, 101L, EntityType.CONTRACT).toEntity();
        contract.setCreatedTimestamp(1L);
        contract.setDeleted(false);
        contract.setMemo("");
        contract.setModifiedTimestamp(1L);
        contract.setParentId(EntityId.of(0L, 0L, 100L, EntityType.CONTRACT));

        // when
        sqlEntityListener.onContract(contract);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(entityRepository.count()).isZero();
        assertThat(contractRepository.findAll())
                .hasSize(1)
                .first()
                .isEqualTo(contract);
    }

    @Test
    void onContractLog() {
        // given
        ContractLog contractLog = domainBuilder.contractLog().get();

        // when
        sqlEntityListener.onContractLog(contractLog);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(contractLogRepository.findAll()).containsExactlyInAnyOrder(contractLog);
    }

    @Test
    void onContractResult() {
        // given
        ContractResult contractResult = domainBuilder.contractResult().get();

        // when
        sqlEntityListener.onContractResult(contractResult);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(contractResultRepository.findAll()).containsExactlyInAnyOrder(contractResult);
    }

    @Test
    void onCryptoTransferList() {
        // given
        CryptoTransfer cryptoTransfer1 = new CryptoTransfer(1L, 1L, EntityId.of(0L, 0L, 1L, ACCOUNT));
        cryptoTransfer1.setPayerAccountId(TRANSACTION_PAYER);
        CryptoTransfer cryptoTransfer2 = new CryptoTransfer(2L, -2L, EntityId.of(0L, 0L, 2L, ACCOUNT));
        cryptoTransfer2.setPayerAccountId(TRANSACTION_PAYER);

        // when
        sqlEntityListener.onCryptoTransfer(cryptoTransfer1);
        sqlEntityListener.onCryptoTransfer(cryptoTransfer2);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrder(cryptoTransfer1, cryptoTransfer2);
    }

    @Test
    void onNonFeeTransfer() {
        // given
        NonFeeTransfer nonFeeTransfer1 = domainBuilder.nonFeeTransfer().customize(n -> n
                .amount(1L)
                .id(new NonFeeTransfer.Id(1L, EntityId.of(0L, 0L, 1L, ACCOUNT)))
                .payerAccountId(TRANSACTION_PAYER)).get();
        NonFeeTransfer nonFeeTransfer2 = domainBuilder.nonFeeTransfer().customize(n -> n
                .amount(2L)
                .id(new NonFeeTransfer.Id(-2L, EntityId.of(0L, 0L, 2L, ACCOUNT)))
                .payerAccountId(TRANSACTION_PAYER)).get();

        // when
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer1);
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer2);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(nonFeeTransferRepository.findAll()).containsExactlyInAnyOrder(nonFeeTransfer1, nonFeeTransfer2);
    }

    @Test
    void onTopicMessage() {
        // given
        TopicMessage topicMessage = getTopicMessage();

        // when
        sqlEntityListener.onTopicMessage(topicMessage);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(topicMessageRepository.findAll()).containsExactlyInAnyOrder(topicMessage);
    }

    @Test
    void onFileData() {
        // given
        FileData expectedFileData = new FileData(11L, Strings.toByteArray("file data"), EntityId
                .of(0, 0, 111, EntityType.FILE), TransactionTypeEnum.CONSENSUSSUBMITMESSAGE.getProtoId());

        // when
        sqlEntityListener.onFileData(expectedFileData);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(fileDataRepository.findAll()).containsExactlyInAnyOrder(expectedFileData);
    }

    @Test
    void onLiveHash() {
        // given
        LiveHash expectedLiveHash = new LiveHash(20L, "live hash".getBytes());

        // when
        sqlEntityListener.onLiveHash(expectedLiveHash);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(liveHashRepository.findAll()).containsExactlyInAnyOrder(expectedLiveHash);
    }

    @Test
    void onTransaction() {
        // given
        var expectedTransaction = makeTransaction();

        // when
        sqlEntityListener.onTransaction(expectedTransaction);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrder(expectedTransaction);
    }

    @Test
    void onEntityId() {
        // given
        EntityId entityId = EntityId.of(0L, 0L, 10L, ACCOUNT);

        // when
        sqlEntityListener.onEntity(entityId.toEntity());
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(getEntityWithDefaultMemo(entityId));
        assertThat(contractRepository.count()).isZero();
    }

    @Test
    void onEntityIdEmpty() {
        // when
        sqlEntityListener.onEntityId(EntityId.EMPTY);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(entityRepository.count()).isZero();
        assertThat(contractRepository.count()).isZero();
    }

    @Test
    void onEntityIdDuplicates() {
        // given
        EntityId entityId = EntityId.of(0L, 0L, 10L, ACCOUNT);

        // when:
        sqlEntityListener.onEntity(entityId.toEntity());
        sqlEntityListener.onEntity(entityId.toEntity()); // duplicate within file
        completeFileAndCommit();

        RecordFile recordFile2 = recordFile(2L, UUID.randomUUID().toString(), null, 1L, null);
        sqlEntityListener.onStart();
        sqlEntityListener.onEntity(entityId.toEntity()); // duplicate across files
        completeFileAndCommit(recordFile2);

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1, recordFile2);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(getEntityWithDefaultMemo(entityId));
        assertThat(contractRepository.count()).isZero();
    }

    @Test
    void onEntityMerge() {
        // given
        Entity entity = getEntity(1, 1L, 1L, 0, "memo", keyFromString(KEY));
        sqlEntityListener.onEntity(entity);

        Entity entityAutoUpdated = getEntity(1, 5L);
        EntityId autoRenewAccountId = EntityId.of("0.0.10", ACCOUNT);
        entityAutoUpdated.setAutoRenewAccountId(autoRenewAccountId);
        entityAutoUpdated.setAutoRenewPeriod(360L);
        sqlEntityListener.onEntity(entityAutoUpdated);

        Entity entityExpirationUpdated = getEntity(1, 10L);
        entityExpirationUpdated.setExpirationTimestamp(720L);
        sqlEntityListener.onEntity(entityExpirationUpdated);

        Entity entitySubmitKeyUpdated = getEntity(1, 15L);
        entitySubmitKeyUpdated.setSubmitKey(keyFromString(KEY2).toByteArray());
        sqlEntityListener.onEntity(entitySubmitKeyUpdated);

        Entity entityMemoUpdated = getEntity(1, 20L);
        entityMemoUpdated.setMemo("memo-updated");
        sqlEntityListener.onEntity(entityMemoUpdated);

        Entity entityMaxAutomaticTokenAssociationsUpdated = getEntity(1, 25L);
        entityMaxAutomaticTokenAssociationsUpdated.setMaxAutomaticTokenAssociations(10);
        sqlEntityListener.onEntity(entityMaxAutomaticTokenAssociationsUpdated);

        Entity entityReceiverSigRequired = getEntity(1, 30L);
        entityReceiverSigRequired.setReceiverSigRequired(true);
        sqlEntityListener.onEntity(entityReceiverSigRequired);

        // when
        completeFileAndCommit();

        // then
        Entity expected = getEntity(1, 1L, 30L, "memo-updated", keyFromString(KEY), autoRenewAccountId, 360L, null,
                720L, 10, true, keyFromString(KEY2));
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(expected);
        assertThat(contractRepository.count()).isZero();
    }

    @Test
    void onEntityEntityIdMerge() {
        // given
        Entity entity = getEntity(1, 1L, 1L, "memo", keyFromString(KEY),
                EntityId.of(0L, 0L, 10L, ACCOUNT), 360L, false, 720L, 0, false, keyFromString(KEY2));
        sqlEntityListener.onEntity(entity);

        EntityId entityId1 = EntityId.of(0L, 0L, 1L, ACCOUNT);
        sqlEntityListener.onEntity(entityId1.toEntity());

        Entity entityUpdated = getEntity(1, 5L);
        entityUpdated.setMemo("memo-updated");
        sqlEntityListener.onEntity(entityUpdated);

        EntityId entityId2 = EntityId.of(0L, 0L, 1L, ACCOUNT);
        sqlEntityListener.onEntity(entityId2.toEntity());

        // when
        completeFileAndCommit();

        // then
        Entity entityMerged = getEntity(1, 1L, 5L, "memo-updated", keyFromString(KEY),
                EntityId.of(0L, 0L, 10L, ACCOUNT), 360L, false, 720L, 0, false, keyFromString(KEY2));
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entityMerged);
        assertThat(contractRepository.count()).isZero();
    }

    @Test
    void onNft() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1", TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.2", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.4", ACCOUNT);
        EntityId treasuryId = EntityId.of("0.0.98", ACCOUNT);
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        Token token1 = getToken(tokenId1, treasuryId, 1L, 1L);
        Token token2 = getToken(tokenId2, treasuryId, 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);
        completeFileAndCommit();

        // create nft 1
        sqlEntityListener.onNft(getNft(tokenId1, 1L, null, 3L, false, metadata1, 3L)); // mint
        sqlEntityListener.onNft(getNft(tokenId1, 1L, accountId1, null, null, null, 3L)); // transfer

        // create nft 2
        sqlEntityListener.onNft(getNft(tokenId2, 1L, null, 4L, false, metadata2, 4L)); // mint
        sqlEntityListener.onNft(getNft(tokenId2, 1L, accountId2, null, null, null, 4L)); // transfer

        completeFileAndCommit();

        Nft nft1 = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // transfer
        Nft nft2 = getNft(tokenId2, 1L, accountId2, 4L, false, metadata2, 4L); // transfer

        assertThat(recordFileRepository.findAll()).containsExactlyInAnyOrder(recordFile1);
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftMintOutOfOrder() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1", TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.2", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.4", ACCOUNT);
        EntityId treasuryId = EntityId.of("0.0.98", ACCOUNT);
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasuryId, 1L, 1L);
        Token token2 = getToken(tokenId2, treasuryId, 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);

        // create nft 1 w transfer coming first
        sqlEntityListener.onNft(getNft(tokenId1, 1L, accountId1, null, null, null, 3L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId1, 1L, null, 3L, false, metadata1, 3L)); // mint

        // create nft 2 w transfer coming first
        sqlEntityListener.onNft(getNft(tokenId2, 1L, accountId2, null, null, null, 4L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId2, 1L, null, 4L, false, metadata2, 4L)); // mint

        completeFileAndCommit();

        Nft nft1 = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // transfer
        Nft nft2 = getNft(tokenId2, 1L, accountId2, 4L, false, metadata2, 4L); // transfer

        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftDomainTransfer() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1", TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.2", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.4", ACCOUNT);
        EntityId treasuryId = EntityId.of("0.0.98", ACCOUNT);
        EntityId accountId3 = EntityId.of("0.0.5", ACCOUNT);
        EntityId accountId4 = EntityId.of("0.0.6", ACCOUNT);
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasuryId, 1L, 1L);
        Token token2 = getToken(tokenId2, treasuryId, 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);

        // create nfts
        Nft nft1Combined = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // mint transfer combined
        Nft nft2Combined = getNft(tokenId2, 1L, accountId2, 4L, false, metadata2, 4L); // mint transfer combined
        sqlEntityListener.onNft(nft1Combined);
        sqlEntityListener.onNft(nft2Combined);
        completeFileAndCommit();
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertEquals(2, nftRepository.count());

        // nft w transfers
        sqlEntityListener.onNft(getNft(tokenId1, 1L, accountId3, null, null, null, 5L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId2, 1L, accountId4, null, null, null, 6L)); // transfer
        completeFileAndCommit();

        Nft nft1 = getNft(tokenId1, 1L, accountId3, 3L, false, metadata1, 5L); // transfer
        Nft nft2 = getNft(tokenId2, 1L, accountId4, 4L, false, metadata2, 6L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftTransferOwnershipAndDelete() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.2", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.3", ACCOUNT);
        EntityId treasury = EntityId.of("0.0.98", ACCOUNT);
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasury, 1L, 1L);
        sqlEntityListener.onToken(token1);

        // create nfts
        Nft nft1Combined = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // mint transfer combined
        Nft nft2Combined = getNft(tokenId1, 2L, accountId2, 4L, false, metadata2, 4L); // mint transfer combined

        sqlEntityListener.onNft(nft1Combined);
        sqlEntityListener.onNft(nft2Combined);

        completeFileAndCommit();
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertEquals(2, nftRepository.count());

        Nft nft1Burn = getNft(tokenId1, 1L, EntityId.EMPTY, null, true, null, 5L); // mint/burn
        Nft nft1BurnTransfer = getNft(tokenId1, 1L, null, null, null, null, 5L); // mint/burn transfer
        sqlEntityListener.onNft(nft1Burn);
        sqlEntityListener.onNft(nft1BurnTransfer);

        Nft nft2Burn = getNft(tokenId1, 2L, EntityId.EMPTY, null, true, null, 6L); // mint/burn
        Nft nft2BurnTransfer = getNft(tokenId1, 2L, null, null, null, null, 6L); // mint/burn transfer
        sqlEntityListener.onNft(nft2Burn);
        sqlEntityListener.onNft(nft2BurnTransfer);

        completeFileAndCommit();
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);

        // expected nfts
        Nft nft1 = getNft(tokenId1, 1L, null, 3L, true, metadata1, 5L); // transfer
        Nft nft2 = getNft(tokenId1, 2L, null, 4L, true, metadata2, 6L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftTransferOwnershipAndDeleteOutOfOrder() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.2", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.3", ACCOUNT);
        EntityId treasury = EntityId.of("0.0.98", ACCOUNT);
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasury, 1L, 1L);
        sqlEntityListener.onToken(token1);
        completeFileAndCommit();

        // create nfts
        Nft nft1Combined = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // mint transfer combined
        Nft nft2Combined = getNft(tokenId1, 2L, accountId2, 4L, false, metadata2, 4L); // mint transfer combined

        sqlEntityListener.onNft(nft1Combined);
        sqlEntityListener.onNft(nft2Combined);

        // nft 1 burn w transfer coming first
        Nft nft1Burn = getNft(tokenId1, 1L, EntityId.EMPTY, null, true, null, 5L); // mint/burn
        Nft nft1BurnTransfer = getNft(tokenId1, 1L, null, null, null, null, 5L); // mint/burn transfer
        sqlEntityListener.onNft(nft1BurnTransfer);
        sqlEntityListener.onNft(nft1Burn);

        // nft 2 burn w transfer coming first
        Nft nft2Burn = getNft(tokenId1, 2L, EntityId.EMPTY, null, true, null, 6L); // mint/burn
        Nft nft2BurnTransfer = getNft(tokenId1, 2L, null, null, null, null, 6L); // mint/burn transfer
        sqlEntityListener.onNft(nft2BurnTransfer);
        sqlEntityListener.onNft(nft2Burn);
        completeFileAndCommit();

        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);

        // expected nfts
        Nft nft1 = getNft(tokenId1, 1L, null, 3L, true, metadata1, 5L); // transfer
        Nft nft2 = getNft(tokenId1, 2L, null, 4L, true, metadata2, 6L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftTransfer() {
        NftTransfer nftTransfer1 = getNftTransfer(1L, "0.0.1", 1L, "0.0.2", "0.0.3");
        NftTransfer nftTransfer2 = getNftTransfer(2L, "0.0.1", 2L, "0.0.2", "0.0.3");
        NftTransfer nftTransfer3 = getNftTransfer(3L, "0.0.2", 1L, "0.0.2", "0.0.3");

        // when
        sqlEntityListener.onNftTransfer(nftTransfer1);
        sqlEntityListener.onNftTransfer(nftTransfer2);
        sqlEntityListener.onNftTransfer(nftTransfer3);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(nftTransferRepository.findAll()).containsExactlyInAnyOrder(nftTransfer1, nftTransfer2, nftTransfer3);
    }

    @Test
    void onToken() {
        Token token1 = getToken(EntityId.of("0.0.3", TOKEN), EntityId.of("0.0.5", ACCOUNT), 1L, 1L);
        Token token2 = getToken(EntityId.of("0.0.7", TOKEN), EntityId.of("0.0.11", ACCOUNT), 2L, 2L);

        // when
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(token1, token2);
    }

    @Test
    void onTokenMerge() {
        EntityId tokenId = EntityId.of("0.0.3", TOKEN);
        EntityId accountId = EntityId.of("0.0.500", ACCOUNT);

        // save token entities first
        Token token = getToken(tokenId, accountId, 1L, 1L, 1000, false, keyFromString(KEY),
                1_000_000_000L, null, "FOO COIN TOKEN", null, "FOOTOK", null, null,
                TokenPauseStatusEnum.UNPAUSED);
        sqlEntityListener.onToken(token);

        Token tokenUpdated = getToken(tokenId, accountId, null, 5L, null, null, null,
                null, keyFromString(KEY2), "BAR COIN TOKEN", keyFromString(KEY), "BARTOK", keyFromString(KEY2),
                keyFromString(KEY2), TokenPauseStatusEnum.UNPAUSED);
        sqlEntityListener.onToken(tokenUpdated);
        completeFileAndCommit();

        // then
        Token tokenMerged = getToken(tokenId, accountId, 1L, 5L, 1000, false, keyFromString(KEY),
                1_000_000_000L, keyFromString(KEY2), "BAR COIN TOKEN", keyFromString(KEY), "BARTOK",
                keyFromString(KEY2), keyFromString(KEY2), TokenPauseStatusEnum.UNPAUSED);
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(tokenMerged);
    }

    @Test
    void onTokenConsecutiveNegativeTotalSupply() {
        // given
        EntityId tokenId = EntityId.of("0.0.3", TOKEN);
        EntityId accountId = EntityId.of("0.0.500", ACCOUNT);

        // save token
        Token token = getToken(tokenId, accountId, 1L, 1L);
        sqlEntityListener.onToken(token);
        completeFileAndCommit();

        // when
        // two dissociate of the deleted token, both with negative amount
        Token update = getTokenUpdate(tokenId, 5);
        update.setTotalSupply(-10L);
        sqlEntityListener.onToken(update);

        update = getTokenUpdate(tokenId, 6);
        update.setTotalSupply(-15L);
        sqlEntityListener.onToken(update);

        completeFileAndCommit(recordFile2);

        // then
        assertThat(recordFileRepository.findAll()).containsExactlyInAnyOrder(recordFile1, recordFile2);
        token.setTotalSupply(token.getTotalSupply() - 25);
        token.setModifiedTimestamp(6);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(token);
    }

    @Test
    void onTokenMergeNegativeTotalSupply() {
        // given
        EntityId tokenId = EntityId.of("0.0.3", TOKEN);
        EntityId accountId = EntityId.of("0.0.500", ACCOUNT);

        // when
        // create token
        Token token = getToken(tokenId, accountId, 1L, 1L);
        sqlEntityListener.onToken(token);

        // token dissociate of the deleted token
        Token update = getTokenUpdate(tokenId, 5);
        update.setTotalSupply(-10L);
        sqlEntityListener.onToken(update);

        completeFileAndCommit();

        // then
        Token expected = getToken(tokenId, accountId, 1L, 5L);
        expected.setTotalSupply(expected.getTotalSupply() - 10);
        assertThat(recordFileRepository.findAll()).containsOnly(recordFile1);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(expected);
    }

    @Test
    void onTokenAccount() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.5", TOKEN);

        // save token entities first
        Token token1 = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        Token token2 = getToken(tokenId2, EntityId.of("0.0.110", ACCOUNT), 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);
        completeFileAndCommit();

        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.11", ACCOUNT);
        TokenAccount tokenAccount1 = getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
        TokenAccount tokenAccount2 = getTokenAccount(tokenId2, accountId2, 6L, 6L, true, false,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);

        // when
        sqlEntityListener.onTokenAccount(tokenAccount1);
        sqlEntityListener.onTokenAccount(tokenAccount2);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(tokenAccount1, tokenAccount2);
    }

    @Test
    void onTokenAccountDissociate() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);

        // save token entities first
        Token token1 = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        sqlEntityListener.onToken(token1);
        completeFileAndCommit();

        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);
        TokenAccount associate = getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
        TokenAccount dissociate = getTokenAccount(tokenId1, accountId1, null, 10L, false, null, null, null);

        // when
        sqlEntityListener.onTokenAccount(associate);
        sqlEntityListener.onTokenAccount(dissociate);
        completeFileAndCommit(recordFile2);

        // then
        assertThat(recordFileRepository.findAll()).containsExactlyInAnyOrder(recordFile1, recordFile2);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(
                associate,
                getTokenAccount(tokenId1, accountId1, 5L, 10L, false, false, TokenFreezeStatusEnum.NOT_APPLICABLE,
                        TokenKycStatusEnum.NOT_APPLICABLE)
        );
    }

    @Test
    void onTokenAccountMerge() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);

        // save token entities first
        Token token = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        sqlEntityListener.onToken(token);

        // when
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);
        TokenAccount tokenAccountAssociate = getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false, null, null);
        sqlEntityListener.onTokenAccount(tokenAccountAssociate);

        TokenAccount tokenAccountKyc = getTokenAccount(tokenId1, accountId1, null, 15L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(tokenAccountKyc);

        completeFileAndCommit();

        // then
        TokenAccount tokenAccountMerged = getTokenAccount(tokenId1, accountId1, 5L, 15L, true, false,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.GRANTED);
        assertThat(recordFileRepository.findAll()).containsOnly(recordFile1);
        assertThat(tokenAccountRepository.findAll()).hasSize(2).contains(tokenAccountMerged);
    }

    @Test
    void onTokenAccountReassociate() {
        List<TokenAccount> expected = new ArrayList<>();
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);

        // save token entities first
        Token token = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        tokenRepository.save(token);

        // token account was associated before this record file
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);
        TokenAccount associate = getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false,
                TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        tokenAccountRepository.save(associate);
        expected.add(associate);

        // when
        TokenAccount freeze = getTokenAccount(tokenId1, accountId1, null, 10L, null, null,
                TokenFreezeStatusEnum.FROZEN, null);
        sqlEntityListener.onTokenAccount(freeze);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 10L, true, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.REVOKED));

        TokenAccount kycGrant = getTokenAccount(tokenId1, accountId1, null, 15L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(kycGrant);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 15L, true, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED));

        TokenAccount dissociate = getTokenAccount(tokenId1, accountId1, null, 16L, false, null, null, null);
        sqlEntityListener.onTokenAccount(dissociate);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 16L, false, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED));

        // associate after dissociate, the token has freeze key with freezeDefault = false, the token also has kyc key,
        // so the new relationship should have UNFROZEN, REVOKED
        TokenAccount reassociate = getTokenAccount(tokenId1, accountId1, 20L, 20L, true, false, null, null);
        sqlEntityListener.onTokenAccount(reassociate);
        expected.add(getTokenAccount(tokenId1, accountId1, 20L, 20L, true, false, TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED));

        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsOnly(recordFile1);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onTokenAccountMissingToken() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);

        // given no token row in db

        // when
        TokenAccount associate = getTokenAccount(tokenId1, accountId1, 10L, 10L, true, false, null, null);
        sqlEntityListener.onTokenAccount(associate);

        TokenAccount kycGrant = getTokenAccount(tokenId1, accountId1, null, 15L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(kycGrant);

        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsOnly(recordFile1);
        assertThat(tokenAccountRepository.count()).isZero();
    }

    @Test
    void onTokenAccountMissingLastAssociation() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);

        // given token in db and missing last account token association
        Token token = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        tokenRepository.save(token);

        // when
        TokenAccount freeze = getTokenAccount(tokenId1, accountId1, null, 10L, null, null,
                TokenFreezeStatusEnum.FROZEN, null);
        sqlEntityListener.onTokenAccount(freeze);

        TokenAccount kycGrant = getTokenAccount(tokenId1, accountId1, null, 15L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(kycGrant);

        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsOnly(recordFile1);
        assertThat(tokenAccountRepository.count()).isZero();
    }

    @Test
    void onTokenAccountSpanningRecordFiles() {
        List<TokenAccount> expected = new ArrayList<>();
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);

        // given token in db
        Token token = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        tokenRepository.save(token);

        // given association in a previous record file
        TokenAccount associate = getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false, null, null);
        sqlEntityListener.onTokenAccount(associate);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false, TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED));

        completeFileAndCommit(recordFile1);

        // when in the next record file we have freeze, kycGrant, dissociate, associate, kycGrant
        TokenAccount freeze = getTokenAccount(tokenId1, accountId1, null, 10L, null, null, TokenFreezeStatusEnum.FROZEN,
                null);
        sqlEntityListener.onTokenAccount(freeze);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 10L, true, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.REVOKED));

        TokenAccount kycGrant = getTokenAccount(tokenId1, accountId1, null, 12L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(kycGrant);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 12L, true, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED));

        TokenAccount dissociate = getTokenAccount(tokenId1, accountId1, null, 15L, false, null, null, null);
        sqlEntityListener.onTokenAccount(dissociate);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 15L, false, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED));

        associate = getTokenAccount(tokenId1, accountId1, 20L, 20L, true, true, null, null);
        sqlEntityListener.onTokenAccount(associate);
        expected.add(getTokenAccount(tokenId1, accountId1, 20L, 20L, true, true, TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED));

        kycGrant = getTokenAccount(tokenId1, accountId1, null, 22L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(kycGrant);
        expected.add(getTokenAccount(tokenId1, accountId1, 20L, 22L, true, true, TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.GRANTED));

        completeFileAndCommit(recordFile2);

        // then
        assertThat(recordFileRepository.findAll()).containsExactlyInAnyOrder(recordFile1, recordFile2);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onTokenTransfer() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.7", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.5", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.9", ACCOUNT);

        TokenTransfer tokenTransfer1 = getTokenTransfer(1000, 2L, tokenId1, accountId1);
        TokenTransfer tokenTransfer2 = getTokenTransfer(50, 2L, tokenId2, accountId2);
        TokenTransfer tokenTransfer3 = getTokenTransfer(-444, 4L, tokenId1, accountId1);

        // when
        sqlEntityListener.onTokenTransfer(tokenTransfer1);
        sqlEntityListener.onTokenTransfer(tokenTransfer2);
        sqlEntityListener.onTokenTransfer(tokenTransfer3);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(tokenTransferRepository.findAll())
                .containsExactlyInAnyOrder(tokenTransfer1, tokenTransfer2, tokenTransfer3);
    }

    @Test
    void onSchedule() {
        EntityId entityId1 = EntityId.of("0.0.100", EntityType.SCHEDULE);
        EntityId entityId2 = EntityId.of("0.0.200", EntityType.SCHEDULE);

        Schedule schedule1 = getSchedule(1, entityId1.entityIdToString());
        Schedule schedule2 = getSchedule(2, entityId2.entityIdToString());

        // when
        sqlEntityListener.onSchedule(schedule1);
        sqlEntityListener.onSchedule(schedule2);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(scheduleRepository.findAll()).containsExactlyInAnyOrder(schedule1, schedule2);
    }

    @Test
    void onScheduleSignature() {
        EntityId entityId1 = EntityId.of("0.0.100", EntityType.SCHEDULE);
        EntityId entityId2 = EntityId.of("0.0.200", EntityType.SCHEDULE);
        EntityId entityId3 = EntityId.of("0.0.300", EntityType.SCHEDULE);
        byte[] pubKeyPrefix1 = "pubKeyPrefix1".getBytes();
        byte[] pubKeyPrefix2 = "pubKeyPrefix2".getBytes();
        byte[] pubKeyPrefix3 = "pubKeyPrefix3".getBytes();

        TransactionSignature transactionSignature1 = getTransactionSignature(1, entityId1
                .entityIdToString(), pubKeyPrefix1);
        TransactionSignature transactionSignature2 = getTransactionSignature(2, entityId2
                .entityIdToString(), pubKeyPrefix2);
        TransactionSignature transactionSignature3 = getTransactionSignature(3, entityId3
                .entityIdToString(), pubKeyPrefix3);

        // when
        sqlEntityListener.onTransactionSignature(transactionSignature1);
        sqlEntityListener.onTransactionSignature(transactionSignature2);
        sqlEntityListener.onTransactionSignature(transactionSignature3);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(transactionSignatureRepository.findAll())
                .containsExactlyInAnyOrder(transactionSignature1, transactionSignature2, transactionSignature3);
    }

    @Test
    void onScheduleMerge() {
        String scheduleId = "0.0.100";
        EntityId entityId = EntityId.of(scheduleId, EntityType.SCHEDULE);

        Schedule schedule = getSchedule(1, entityId.entityIdToString());
        sqlEntityListener.onSchedule(schedule);

        Schedule scheduleUpdated = new Schedule();
        scheduleUpdated.setScheduleId(EntityId.of(scheduleId, EntityType.SCHEDULE));
        scheduleUpdated.setExecutedTimestamp(5L);
        sqlEntityListener.onSchedule(scheduleUpdated);

        // when
        completeFileAndCommit();

        // then
        Schedule scheduleMerged = getSchedule(1, entityId.entityIdToString());
        scheduleMerged.setExecutedTimestamp(5L);
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile1);
        assertThat(scheduleRepository.findAll()).containsExactlyInAnyOrder(scheduleMerged);
    }

    private void completeFileAndCommit() {
        completeFileAndCommit(recordFile1);
    }

    private void completeFileAndCommit(RecordFile recordFile) {
        transactionTemplate.executeWithoutResult(status -> sqlEntityListener.onEnd(clone(recordFile)));
    }

    private RecordFile recordFile(long consensusStart, String filename, String fileHash, long index, String prevHash) {
        if (fileHash == null) {
            fileHash = UUID.randomUUID().toString();
        }
        if (prevHash == null) {
            prevHash = UUID.randomUUID().toString();
        }

        EntityId nodeAccountId = EntityId.of("0.0.3", ACCOUNT);
        RecordFile rf = RecordFile.builder()
                .consensusStart(consensusStart)
                .consensusEnd(consensusStart + 1)
                .count(1L)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .fileHash(fileHash)
                .hash(fileHash)
                .index(index)
                .loadEnd(Instant.now().getEpochSecond())
                .loadStart(Instant.now().getEpochSecond())
                .name(filename)
                .nodeAccountId(nodeAccountId)
                .previousHash(prevHash)
                .build();
        return rf;
    }

    private Entity getEntity(long id, long modifiedTimestamp) {
        return getEntity(id, null, modifiedTimestamp, null, null, null);
    }

    private Entity getEntity(long id, Long createdTimestamp, long modifiedTimestamp,
                             Integer maxAutomaticTokenAssociations, String memo, Key adminKey) {
        return getEntity(id, createdTimestamp, modifiedTimestamp, memo, adminKey, null, null, null, null,
                maxAutomaticTokenAssociations, false, null);
    }

    private Entity getEntity(long id, Long createdTimestamp, long modifiedTimestamp, String memo,
                             Key adminKey, EntityId autoRenewAccountId, Long autoRenewPeriod,
                             Boolean deleted, Long expiryTimeNs, Integer maxAutomaticTokenAssociations,
                             Boolean receiverSigRequired, Key submitKey) {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setAutoRenewAccountId(autoRenewAccountId);
        entity.setAutoRenewPeriod(autoRenewPeriod);
        entity.setCreatedTimestamp(createdTimestamp);
        entity.setDeleted(deleted);
        entity.setExpirationTimestamp(expiryTimeNs);
        entity.setKey(adminKey != null ? adminKey.toByteArray() : null);
        entity.setMaxAutomaticTokenAssociations(maxAutomaticTokenAssociations);
        entity.setModifiedTimestamp(modifiedTimestamp);
        entity.setNum(id);
        entity.setRealm(0L);
        entity.setReceiverSigRequired(receiverSigRequired);
        entity.setShard(0L);
        entity.setSubmitKey(submitKey != null ? submitKey.toByteArray() : null);
        entity.setType(ACCOUNT);
        if (memo != null) {
            entity.setMemo(memo);
        }
        return entity;
    }

    private Transaction makeTransaction() {
        EntityId entityId = EntityId.of(10, 10, 10, ACCOUNT);
        Transaction transaction = new Transaction();
        transaction.setConsensusTimestamp(101L);
        transaction.setEntityId(entityId);
        transaction.setNodeAccountId(entityId);
        transaction.setMemo("memo".getBytes());
        transaction.setType(14);
        transaction.setResult(TransactionResult.SUCCESS);
        transaction.setTransactionHash("transaction hash".getBytes());
        transaction.setTransactionBytes("transaction bytes".getBytes());
        transaction.setPayerAccountId(entityId);
        transaction.setValidStartNs(1L);
        transaction.setValidDurationSeconds(1L);
        transaction.setMaxFee(1L);
        transaction.setChargedTxFee(1L);
        transaction.setInitialBalance(0L);
        transaction.setScheduled(true);
        return transaction;
    }

    private TopicMessage getTopicMessage() {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setChunkNum(1);
        topicMessage.setChunkTotal(2);
        topicMessage.setConsensusTimestamp(1L);
        topicMessage.setMessage("test message".getBytes());
        topicMessage.setPayerAccountId(EntityId.of("0.1.1000", EntityType.ACCOUNT));
        topicMessage.setRunningHash("running hash".getBytes());
        topicMessage.setRunningHashVersion(2);
        topicMessage.setSequenceNumber(1L);
        topicMessage.setTopicId(EntityId.of("0.0.1001", EntityType.TOPIC));
        topicMessage.setValidStartTimestamp(4L);

        return topicMessage;
    }

    @SneakyThrows
    private Token getToken(EntityId tokenId, EntityId accountId, Long createdTimestamp, long modifiedTimestamp) {
        var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var hexKey = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(instr))).build();
        return getToken(tokenId, accountId, createdTimestamp, modifiedTimestamp, 1000, false, hexKey,
                1_000_000_000L, hexKey, "FOO COIN TOKEN", hexKey, "FOOTOK", hexKey, hexKey,
                TokenPauseStatusEnum.UNPAUSED);
    }

    private Token getToken(EntityId tokenId, EntityId accountId, Long createdTimestamp, long modifiedTimestamp,
                           Integer decimals, Boolean freezeDefault, Key freezeKey, Long initialSupply, Key kycKey,
                           String name, Key supplyKey, String symbol, Key wipeKey, Key pauseKey,
                           TokenPauseStatusEnum pauseStatus) {
        Token token = new Token();
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(decimals);
        token.setFreezeDefault(freezeDefault);
        token.setFreezeKey(freezeKey != null ? freezeKey.toByteArray() : null);
        token.setInitialSupply(initialSupply);
        token.setKycKey(kycKey != null ? kycKey.toByteArray() : null);
        token.setMaxSupply(0L);
        token.setModifiedTimestamp(modifiedTimestamp);
        token.setName(name);
        token.setPauseKey(pauseKey != null ? pauseKey.toByteArray() : null);
        token.setPauseStatus(pauseStatus);
        token.setSupplyKey(supplyKey != null ? supplyKey.toByteArray() : null);
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setSymbol(symbol);
        token.setTokenId(new TokenId(tokenId));
        token.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        token.setTreasuryAccountId(accountId);
        token.setWipeKey(wipeKey != null ? wipeKey.toByteArray() : null);

        return token;
    }

    private Token getTokenUpdate(EntityId tokenId, long modifiedTimestamp) {
        Token token = Token.of(tokenId);
        token.setModifiedTimestamp(modifiedTimestamp);
        return token;
    }

    private Nft getNft(EntityId tokenId, long serialNumber, EntityId accountId, Long createdTimestamp, Boolean deleted,
                       String metadata, long modifiedTimestamp) {
        Nft nft = new Nft();
        nft.setAccountId(accountId);
        nft.setCreatedTimestamp(createdTimestamp);
        nft.setDeleted(deleted);
        nft.setMetadata(metadata == null ? null : metadata.getBytes(StandardCharsets.UTF_8));
        nft.setId(new NftId(serialNumber, tokenId));
        nft.setModifiedTimestamp(modifiedTimestamp);

        return nft;
    }

    private NftTransfer getNftTransfer(long consensusTimestamp, String tokenId, long serialNumber, String receiverId,
                                       String senderId) {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(consensusTimestamp, serialNumber, EntityId.of(tokenId, TOKEN)));
        nftTransfer.setReceiverAccountId(EntityId.of(receiverId, ACCOUNT));
        nftTransfer.setSenderAccountId(EntityId.of(senderId, ACCOUNT));
        nftTransfer.setPayerAccountId(TRANSACTION_PAYER);
        return nftTransfer;
    }

    private TokenAccount getTokenAccount(EntityId tokenId, EntityId accountId, Long createdTimestamp,
                                         long modifiedTimeStamp, Boolean associated, Boolean autoAssociated,
                                         TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus) {
        TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, modifiedTimeStamp);
        tokenAccount.setAssociated(associated);
        tokenAccount.setAutomaticAssociation(autoAssociated);
        tokenAccount.setFreezeStatus(freezeStatus);
        tokenAccount.setKycStatus(kycStatus);
        tokenAccount.setCreatedTimestamp(createdTimestamp);

        return tokenAccount;
    }

    private TokenTransfer getTokenTransfer(long amount, long consensusTimestamp, EntityId tokenId, EntityId accountId) {
        TokenTransfer tokenTransfer = new TokenTransfer();
        tokenTransfer.setAmount(amount);
        tokenTransfer
                .setId(new TokenTransfer.Id(consensusTimestamp, tokenId, accountId));
        tokenTransfer.setPayerAccountId(TRANSACTION_PAYER);

        return tokenTransfer;
    }

    private Schedule getSchedule(long consensusTimestamp, String scheduleId) {
        Schedule schedule = new Schedule();
        schedule.setScheduleId(EntityId.of(scheduleId, EntityType.SCHEDULE));
        schedule.setConsensusTimestamp(consensusTimestamp);
        schedule.setCreatorAccountId(EntityId.of("0.0.123", EntityType.ACCOUNT));
        schedule.setPayerAccountId(EntityId.of("0.0.456", EntityType.ACCOUNT));
        schedule.setTransactionBody("transaction body".getBytes());
        return schedule;
    }

    private TransactionSignature getTransactionSignature(long consensusTimestamp, String scheduleId,
                                                         byte[] pubKeyPrefix) {
        TransactionSignature transactionSignature = new TransactionSignature();
        transactionSignature.setId(new TransactionSignature.Id(
                consensusTimestamp,
                pubKeyPrefix));
        transactionSignature.setEntityId(EntityId.of(scheduleId, EntityType.SCHEDULE));
        transactionSignature.setSignature("scheduled transaction signature".getBytes());
        return transactionSignature;
    }

    private RecordFile clone(RecordFile recordFile) {
        return recordFile.toBuilder().build();
    }

    protected Entity getEntityWithDefaultMemo(EntityId entityId) {
        Entity entity = entityId.toEntity();
        entity.setMemo("");
        return entity;
    }
}
