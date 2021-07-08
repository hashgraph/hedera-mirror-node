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
public class RepositoryEntityListenerTest extends IntegrationTest {

    private static final EntityId ENTITY_ID = EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT);
    private static final EntityId TOKEN_ID = EntityId.of("0.0.7", EntityTypeEnum.TOKEN);

    private final RepositoryProperties repositoryProperties;
    private final ContractResultRepository contractResultRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final FileDataRepository fileDataRepository;
    private final LiveHashRepository liveHashRepository;
    private final NftRepository nftRepository;
    private final NftTransferRepository nftTransferRepository;
    private final NonFeeTransferRepository nonFeeTransferRepository;
    private final TokenRepository tokenRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final TransactionRepository transactionRepository;
    private final RepositoryEntityListener repositoryEntityListener;
    private final ScheduleRepository scheduleRepository;
    private final TransactionSignatureRepository transactionSignatureRepository;

    private final JdbcTemplate jdbcTemplate;

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
        assertThat(contractResultRepository.findAll()).contains(contractResult);
    }

    @Test
    void onCryptoTransfer() {
        CryptoTransfer cryptoTransfer = new CryptoTransfer(1L, 100L, ENTITY_ID);
        repositoryEntityListener.onCryptoTransfer(cryptoTransfer);
        assertThat(cryptoTransferRepository.findAll()).contains(cryptoTransfer);
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
    void onFileData() {
        FileData fileData = new FileData();
        fileData.setConsensusTimestamp(1L);
        fileData.setEntityId(ENTITY_ID);
        fileData.setFileData(new byte[] {'a'});
        fileData.setTransactionType(1);
        repositoryEntityListener.onFileData(fileData);
        assertThat(fileDataRepository.findAll()).contains(fileData);
    }

    @Test
    void onLiveHash() {
        LiveHash liveHash = new LiveHash();
        liveHash.setConsensusTimestamp(1L);
        repositoryEntityListener.onLiveHash(liveHash);
        assertThat(liveHashRepository.findAll()).contains(liveHash);
    }

    @Test
    void onNonFeeTransfer() {
        NonFeeTransfer nonFeeTransfer = new NonFeeTransfer();
        nonFeeTransfer.setAmount(100L);
        nonFeeTransfer.setId(new NonFeeTransfer.Id(1L, ENTITY_ID));
        repositoryEntityListener.onNonFeeTransfer(nonFeeTransfer);
        assertThat(nonFeeTransferRepository.findAll()).contains(nonFeeTransfer);
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
        assertThat(nftRepository.findAll()).contains(nft);
    }

    @Test
    void onNftTransfer() {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(1L, 1L, EntityId.of("0.0.123", EntityTypeEnum.TOKEN)));
        nftTransfer.setReceiverAccountId(EntityId.of("0.0.456", EntityTypeEnum.ACCOUNT));
        nftTransfer.setSenderAccountId(EntityId.of("0.0.789", EntityTypeEnum.ACCOUNT));
        repositoryEntityListener.onNftTransfer(nftTransfer);
        assertThat(nftTransferRepository.findAll()).contains(nftTransfer);
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
        assertThat(scheduleRepository.findAll()).contains(schedule);
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
        assertThat(transactionSignatureRepository.findAll()).contains(transactionSignature);
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
        token.setModifiedTimestamp(3L);
        token.setName("FOO COIN TOKEN");
        token.setSupplyKey(input.toByteArray());
        token.setSupplyType(TokenSupplyTypeEnum.FINITE);
        token.setSymbol("FOOTOK");
        token.setTokenId(new TokenId(TOKEN_ID));
        token.setTreasuryAccountId(ENTITY_ID);
        token.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        token.setWipeKey(input.toByteArray());
        repositoryEntityListener.onToken(token);
        assertThat(tokenRepository.findAll()).contains(token);
    }

    @Test
    void onTokenAccount() throws ImporterException {
        TokenAccount tokenAccount = new TokenAccount(TOKEN_ID, ENTITY_ID);
        tokenAccount.setAssociated(true);
        tokenAccount.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        tokenAccount.setCreatedTimestamp(1L);
        tokenAccount.setModifiedTimestamp(2L);
        repositoryEntityListener.onTokenAccount(tokenAccount);
        assertThat(tokenAccountRepository.findAll()).contains(tokenAccount);
    }

    @Test
    void onTokenTransfer() throws ImporterException {
        TokenTransfer tokenTransfer = new TokenTransfer();
        tokenTransfer.setAmount(1000);
        tokenTransfer.setId(new TokenTransfer.Id(2L, TOKEN_ID, ENTITY_ID));
        repositoryEntityListener.onTokenTransfer(tokenTransfer);
        assertThat(tokenTransferRepository.findAll()).contains(tokenTransfer);
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
        assertThat(topicMessageRepository.findAll()).contains(topicMessage);
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
        assertThat(transactionRepository.findAll()).contains(transaction);
    }
}
