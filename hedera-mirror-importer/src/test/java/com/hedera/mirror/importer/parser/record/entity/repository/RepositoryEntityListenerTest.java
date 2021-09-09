package com.hedera.mirror.importer.parser.record.entity.repository;

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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AssessedCustomFee;
import com.hedera.mirror.importer.domain.AssessedCustomFeeWrapper;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.CustomFee;
import com.hedera.mirror.importer.domain.CustomFeeWrapper;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftId;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NftTransferId;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenId;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenSupplyTypeEnum;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TokenTypeEnum;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionSignature;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.repository.NonFeeTransferRepository;
import com.hedera.mirror.importer.repository.ScheduleRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.repository.TransactionSignatureRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class RepositoryEntityListenerTest extends IntegrationTest {

    private static final EntityId ENTITY_ID = EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT);
    private static final EntityId TOKEN_ID = EntityId.of("0.0.7", EntityTypeEnum.TOKEN);

    private final ContractResultRepository contractResultRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final FileDataRepository fileDataRepository;
    private final JdbcTemplate jdbcTemplate;
    private final LiveHashRepository liveHashRepository;
    private final NftRepository nftRepository;
    private final NftTransferRepository nftTransferRepository;
    private final NonFeeTransferRepository nonFeeTransferRepository;
    private final RepositoryEntityListener repositoryEntityListener;
    private final RepositoryProperties repositoryProperties;
    private final ScheduleRepository scheduleRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenRepository tokenRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionSignatureRepository transactionSignatureRepository;

    @Test
    void isEnabled() {
        repositoryProperties.setEnabled(false);
        assertThat(repositoryEntityListener.isEnabled()).isFalse();

        repositoryProperties.setEnabled(true);
        assertThat(repositoryEntityListener.isEnabled()).isTrue();
        repositoryProperties.setEnabled(false);
    }

    @Test
    void onAssessedCustomFee() {
        AssessedCustomFee assessedCustomFee1 = new AssessedCustomFee();
        assessedCustomFee1.setAmount(10L);
        assessedCustomFee1.setId(new AssessedCustomFee.Id(ENTITY_ID, 1010L));

        AssessedCustomFee assessedCustomFee2 = new AssessedCustomFee();
        assessedCustomFee2.setAmount(11L);
        assessedCustomFee2.setEffectivePayerAccountIds(List.of(1002L, 1003L));
        assessedCustomFee2.setId(new AssessedCustomFee.Id(ENTITY_ID, 1031L));
        assessedCustomFee2.setTokenId(TOKEN_ID);

        repositoryEntityListener.onAssessedCustomFee(assessedCustomFee1);
        repositoryEntityListener.onAssessedCustomFee(assessedCustomFee2);

        var actual = jdbcTemplate.query(AssessedCustomFeeWrapper.SELECT_QUERY, AssessedCustomFeeWrapper.ROW_MAPPER);
        assertThat(actual)
                .map(AssessedCustomFeeWrapper::getAssessedCustomFee)
                .containsExactlyInAnyOrder(assessedCustomFee1, assessedCustomFee2);
    }

    @Test
    void onContractResult() {
        ContractResult contractResult = new ContractResult();
        contractResult.setConsensusTimestamp(1L);
        repositoryEntityListener.onContractResult(contractResult);
        assertThat(contractResultRepository.findAll()).containsExactly(contractResult);
    }

    @Test
    void onCryptoTransfer() {
        CryptoTransfer cryptoTransfer = new CryptoTransfer(1L, 100L, ENTITY_ID);
        repositoryEntityListener.onCryptoTransfer(cryptoTransfer);
        assertThat(cryptoTransferRepository.findAll()).containsExactly(cryptoTransfer);
    }

    @Test
    void onCustomFee() {
        CustomFee customFee1 = new CustomFee();
        customFee1.setId(new CustomFee.Id(1010L, TOKEN_ID));

        CustomFee customFee2 = new CustomFee();
        customFee2.setAmount(33L);
        customFee2.setCollectorAccountId(ENTITY_ID);
        customFee2.setId(new CustomFee.Id(1020L, TOKEN_ID));

        CustomFee customFee3 = new CustomFee();
        customFee3.setAmount(33L);
        customFee3.setAmountDenominator(47L);
        customFee3.setCollectorAccountId(ENTITY_ID);
        customFee3.setId(new CustomFee.Id(1020L, TOKEN_ID));

        repositoryEntityListener.onCustomFee(customFee1);
        repositoryEntityListener.onCustomFee(customFee2);
        repositoryEntityListener.onCustomFee(customFee3);

        var actual = jdbcTemplate.query(CustomFeeWrapper.SELECT_QUERY, CustomFeeWrapper.ROW_MAPPER);
        assertThat(actual)
                .map(CustomFeeWrapper::getCustomFee)
                .containsExactlyInAnyOrder(customFee1, customFee2, customFee3);
    }

    @Test
    void onEntity() {
        Entity entity = ENTITY_ID.toEntity();
        entity.setAutoRenewAccountId(EntityId.of("0.0.100", EntityTypeEnum.ACCOUNT));
        entity.setAutoRenewPeriod(90L);
        entity.setCreatedTimestamp(1L);
        entity.setDeleted(false);
        entity.setExpirationTimestamp(2L);
        entity.setKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("abc")).build().toByteArray());
        entity.setMemo("test");
        entity.setModifiedTimestamp(1L);
        entity.setProxyAccountId(EntityId.of("0.0.101", EntityTypeEnum.ACCOUNT));
        entity.setSubmitKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("abc")).build().toByteArray());
        repositoryEntityListener.onEntity(entity);
        assertThat(entityRepository.findAll()).containsExactly(entity);

        Entity entityNotUpdated = ENTITY_ID.toEntity();
        repositoryEntityListener.onEntity(entityNotUpdated);
        assertThat(entityRepository.findAll()).containsExactly(entity);

        Entity entityUpdated = ENTITY_ID.toEntity();
        entityUpdated.setAutoRenewAccountId(EntityId.of("0.0.200", EntityTypeEnum.ACCOUNT));
        entityUpdated.setAutoRenewPeriod(180L);
        entityUpdated.setDeleted(true);
        entityUpdated.setExpirationTimestamp(2L);
        entityUpdated.setKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("xyz")).build().toByteArray());
        entityUpdated.setMemo("test2");
        entityUpdated.setModifiedTimestamp(2L);
        entityUpdated.setProxyAccountId(EntityId.of("0.0.201", EntityTypeEnum.ACCOUNT));
        entityUpdated.setSubmitKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("xyz")).build().toByteArray());
        repositoryEntityListener.onEntity(entityUpdated);
        assertThat(entityRepository.findAll())
                .hasSize(1)
                .first()
                .returns(entity.getCreatedTimestamp(), Entity::getCreatedTimestamp)
                .usingRecursiveComparison()
                .ignoringFields("createdTimestamp")
                .isEqualTo(entityUpdated);
    }

    @Test
    void onEntityNullMemo() {
        Entity entity = ENTITY_ID.toEntity();
        repositoryEntityListener.onEntity(entity);
        assertThat(entityRepository.findAll()).first().extracting(Entity::getMemo).isEqualTo("");
    }

    @Test
    void onFileData() {
        FileData fileData = new FileData();
        fileData.setConsensusTimestamp(1L);
        fileData.setEntityId(ENTITY_ID);
        fileData.setFileData(new byte[] {'a'});
        fileData.setTransactionType(1);
        repositoryEntityListener.onFileData(fileData);
        assertThat(fileDataRepository.findAll()).containsExactly(fileData);
    }

    @Test
    void onLiveHash() {
        LiveHash liveHash = new LiveHash();
        liveHash.setConsensusTimestamp(1L);
        repositoryEntityListener.onLiveHash(liveHash);
        assertThat(liveHashRepository.findAll()).containsExactly(liveHash);
    }

    @Test
    void onNonFeeTransfer() {
        NonFeeTransfer nonFeeTransfer = new NonFeeTransfer();
        nonFeeTransfer.setAmount(100L);
        nonFeeTransfer.setId(new NonFeeTransfer.Id(1L, ENTITY_ID));
        repositoryEntityListener.onNonFeeTransfer(nonFeeTransfer);
        assertThat(nonFeeTransferRepository.findAll()).containsExactly(nonFeeTransfer);
    }

    @Test
    void onNft() {
        Nft nft = new Nft();
        nft.setAccountId(EntityId.of("0.0.123", EntityTypeEnum.ACCOUNT));
        nft.setCreatedTimestamp(1L);
        nft.setMetadata(new byte[1]);
        nft.setModifiedTimestamp(2L);
        nft.setId(new NftId(3L, EntityId.of("0.0.456", EntityTypeEnum.TOKEN)));
        repositoryEntityListener.onNft(nft);
        assertThat(nftRepository.findAll()).containsExactly(nft);

        Nft nftMinimallyUpdated = new Nft();
        nftMinimallyUpdated.setId(nft.getId());
        nftMinimallyUpdated.setModifiedTimestamp(3L);
        repositoryEntityListener.onNft(nftMinimallyUpdated);
        assertThat(nftRepository.findAll())
                .hasSize(1)
                .first()
                .returns(nftMinimallyUpdated.getModifiedTimestamp(), Nft::getModifiedTimestamp)
                .usingRecursiveComparison()
                .ignoringFields("modifiedTimestamp")
                .isEqualTo(nft);

        Nft nftUpdated = new Nft();
        nftUpdated.setAccountId(EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT));
        nftUpdated.setDeleted(true);
        nftUpdated.setId(nft.getId());
        nftUpdated.setMetadata(null);
        nftUpdated.setModifiedTimestamp(4L);
        repositoryEntityListener.onNft(nftUpdated);
        assertThat(nftRepository.findAll())
                .hasSize(1)
                .first()
                .returns(nftUpdated.getAccountId(), Nft::getAccountId)
                .returns(nftUpdated.getDeleted(), Nft::getDeleted)
                .returns(nftUpdated.getModifiedTimestamp(), Nft::getModifiedTimestamp)
                .usingRecursiveComparison()
                .ignoringFields("accountId", "deleted", "modifiedTimestamp")
                .isEqualTo(nft);
    }

    @Test
    void onNftTransfer() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1L, 1L, EntityId.of("0.0.123", EntityTypeEnum.TOKEN)));
        nftTransfer.setReceiverAccountId(EntityId.of("0.0.456", EntityTypeEnum.ACCOUNT));
        nftTransfer.setSenderAccountId(EntityId.of("0.0.789", EntityTypeEnum.ACCOUNT));
        repositoryEntityListener.onNftTransfer(nftTransfer);
        assertThat(nftTransferRepository.findAll()).containsExactly(nftTransfer);
    }

    @Test
    void onSchedule() throws ImporterException {
        Schedule schedule = new Schedule();
        schedule.setConsensusTimestamp(1L);
        schedule.setCreatorAccountId(EntityId.of("0.0.123", EntityTypeEnum.ACCOUNT));
        schedule.setPayerAccountId(EntityId.of("0.0.456", EntityTypeEnum.ACCOUNT));
        schedule.setScheduleId(EntityId.of("0.0.789", EntityTypeEnum.SCHEDULE));
        schedule.setTransactionBody("transaction body".getBytes());
        repositoryEntityListener.onSchedule(schedule);
        assertThat(scheduleRepository.findAll()).containsExactly(schedule);

        Schedule scheduleNotUpdated = new Schedule();
        scheduleNotUpdated.setConsensusTimestamp(schedule.getConsensusTimestamp());
        scheduleNotUpdated.setScheduleId(schedule.getScheduleId());
        repositoryEntityListener.onSchedule(scheduleNotUpdated);
        assertThat(scheduleRepository.findAll()).containsExactly(schedule);

        Schedule scheduleUpdated = new Schedule();
        scheduleUpdated.setScheduleId(schedule.getScheduleId());
        scheduleUpdated.setExecutedTimestamp(2L);
        repositoryEntityListener.onSchedule(scheduleUpdated);
        assertThat(scheduleRepository.findAll())
                .hasSize(1)
                .first()
                .returns(scheduleUpdated.getExecutedTimestamp(), Schedule::getExecutedTimestamp)
                .usingRecursiveComparison()
                .ignoringFields("executedTimestamp")
                .isEqualTo(schedule);
    }

    @Test
    void onScheduleSignature() throws ImporterException {
        TransactionSignature transactionSignature = new TransactionSignature();
        transactionSignature.setId(new TransactionSignature.Id(
                1L,
                "signatory public key prefix".getBytes()));
        transactionSignature.setEntityId(EntityId.of("0.0.789", EntityTypeEnum.SCHEDULE));
        transactionSignature.setSignature("scheduled transaction signature".getBytes());
        repositoryEntityListener.onTransactionSignature(transactionSignature);
        assertThat(transactionSignatureRepository.findAll()).containsExactly(transactionSignature);
    }

    @Test
    void onToken() throws ImporterException, DecoderException {
        var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var input = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(instr))).build();
        Token token = new Token();
        token.setCreatedTimestamp(1L);
        token.setDecimals(1000);
        token.setFreezeDefault(false);
        token.setFreezeKey(input.toByteArray());
        token.setInitialSupply(1_000_000_000L);
        token.setKycKey(input.toByteArray());
        token.setMaxSupply(1_000_000_000L);
        token.setModifiedTimestamp(1L);
        token.setName("FOO COIN TOKEN");
        token.setSupplyKey(input.toByteArray());
        token.setSupplyType(TokenSupplyTypeEnum.FINITE);
        token.setSymbol("FOOTOK");
        token.setTokenId(new TokenId(TOKEN_ID));
        token.setTotalSupply(1L);
        token.setTreasuryAccountId(ENTITY_ID);
        token.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        token.setWipeKey(input.toByteArray());
        repositoryEntityListener.onToken(token);
        assertThat(tokenRepository.findAll()).containsExactly(token);

        Token tokenMinimallyUpdated = new Token();
        tokenMinimallyUpdated.setTokenId(token.getTokenId());
        repositoryEntityListener.onToken(tokenMinimallyUpdated);
        assertThat(tokenRepository.findAll())
                .hasSize(1)
                .first()
                .returns(tokenMinimallyUpdated.getModifiedTimestamp(), Token::getModifiedTimestamp)
                .usingRecursiveComparison()
                .ignoringFields("modifiedTimestamp");

        Token tokenUpdated = new Token();
        tokenUpdated.setFreezeKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("abc")).build().toByteArray());
        tokenUpdated.setKycKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("def")).build().toByteArray());
        tokenUpdated.setModifiedTimestamp(2L);
        tokenUpdated.setName("test");
        tokenUpdated.setSupplyKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("ghi")).build().toByteArray());
        tokenUpdated.setSymbol("test");
        tokenUpdated.setTotalSupply(2L);
        tokenUpdated.setTreasuryAccountId(EntityId.of("0.0.2", EntityTypeEnum.ACCOUNT));
        tokenUpdated.setWipeKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("jkl")).build().toByteArray());
        tokenUpdated.setTokenId(token.getTokenId());
        repositoryEntityListener.onToken(tokenUpdated);
        assertThat(tokenRepository.findAll())
                .hasSize(1)
                .first()
                .usingRecursiveComparison()
                .ignoringFields("createdTimestamp", "decimals", "freezeDefault", "initialSupply", "maxSupply",
                        "supplyType", "type")
                .isEqualTo(tokenUpdated);
    }

    @Test
    void onTokenAccount() throws ImporterException {
        TokenAccount associated = new TokenAccount(TOKEN_ID, ENTITY_ID, 1L);
        associated.setAssociated(true);
        associated.setAutomaticAssociation(false);
        associated.setCreatedTimestamp(1L);
        associated.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        associated.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        repositoryEntityListener.onTokenAccount(associated);
        assertThat(tokenAccountRepository.findAll()).containsExactly(associated);

        TokenAccount updated = new TokenAccount(TOKEN_ID, ENTITY_ID, 2L);
        repositoryEntityListener.onTokenAccount(updated);
        assertThat(tokenAccountRepository.findLastByTokenIdAndAccountId(TOKEN_ID.getId(), ENTITY_ID.getId()))
                .get()
                .returns(updated.getId(), TokenAccount::getId)
                .usingRecursiveComparison()
                .ignoringFields("id")
                .isEqualTo(associated);

        updated = new TokenAccount(TOKEN_ID, ENTITY_ID, 3L);
        updated.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        updated.setKycStatus(TokenKycStatusEnum.GRANTED);
        repositoryEntityListener.onTokenAccount(updated);
        assertThat(tokenAccountRepository.findLastByTokenIdAndAccountId(TOKEN_ID.getId(), ENTITY_ID.getId()))
                .get()
                .returns(updated.getFreezeStatus(), TokenAccount::getFreezeStatus)
                .returns(updated.getId(), TokenAccount::getId)
                .returns(updated.getKycStatus(), TokenAccount::getKycStatus)
                .usingRecursiveComparison()
                .ignoringFields("freezeStatus", "id", "kycStatus")
                .isEqualTo(associated);

        TokenAccount dissociated = new TokenAccount(TOKEN_ID, ENTITY_ID, 4L);
        dissociated.setAssociated(false);
        repositoryEntityListener.onTokenAccount(dissociated);
        assertThat(tokenAccountRepository.findLastByTokenIdAndAccountId(TOKEN_ID.getId(), ENTITY_ID.getId()))
                .get()
                .returns(dissociated.getId(), TokenAccount::getId)
                .returns(false, TokenAccount::getAssociated)
                .returns(associated.getAutomaticAssociation(), TokenAccount::getAutomaticAssociation)
                .returns(associated.getCreatedTimestamp(), TokenAccount::getCreatedTimestamp)
                .returns(updated.getFreezeStatus(), TokenAccount::getFreezeStatus)
                .returns(updated.getKycStatus(), TokenAccount::getKycStatus);

        assertThat(tokenAccountRepository.count()).isEqualTo(4);
    }

    @Test
    void onTokenTransfer() throws ImporterException {
        TokenTransfer tokenTransfer = new TokenTransfer();
        tokenTransfer.setAmount(1000);
        tokenTransfer.setId(new TokenTransfer.Id(2L, TOKEN_ID, ENTITY_ID));
        repositoryEntityListener.onTokenTransfer(tokenTransfer);
        assertThat(tokenTransferRepository.findAll()).containsExactly(tokenTransfer);
    }

    @Test
    void onTopicMessage() {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setConsensusTimestamp(1L);
        topicMessage.setMessage(new byte[] {'a'});
        topicMessage.setRealmNum(0);
        topicMessage.setRunningHash(new byte[] {'b'});
        topicMessage.setRunningHashVersion(2);
        topicMessage.setSequenceNumber(1);
        topicMessage.setTopicNum(1);
        repositoryEntityListener.onTopicMessage(topicMessage);
        assertThat(topicMessageRepository.findAll()).containsExactly(topicMessage);
    }

    @Test
    void onTransaction() {
        Transaction transaction = new Transaction();
        transaction.setConsensusNs(1L);
        transaction.setNodeAccountId(ENTITY_ID);
        transaction.setPayerAccountId(ENTITY_ID);
        transaction.setResult(1);
        transaction.setType(1);
        transaction.setValidStartNs(1L);
        repositoryEntityListener.onTransaction(transaction);
        assertThat(transactionRepository.findAll()).containsExactly(transaction);
    }
}
