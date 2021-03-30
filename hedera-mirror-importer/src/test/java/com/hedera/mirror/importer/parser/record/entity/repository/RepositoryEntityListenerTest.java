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

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionSignature;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
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
    private final NonFeeTransferRepository nonFeeTransferRepository;
    private final TokenRepository tokenRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final TransactionRepository transactionRepository;
    private final RepositoryEntityListener repositoryEntityListener;
    private final ScheduleRepository scheduleRepository;
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
    void onEntityId() {
        repositoryEntityListener.onEntityId(ENTITY_ID);
        assertThat(entityRepository.findAll()).contains(ENTITY_ID.toEntity());
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
        token.setModifiedTimestamp(3L);
        token.setName("FOO COIN TOKEN");
        token.setSupplyKey(input.toByteArray());
        token.setSymbol("FOOTOK");
        token.setTokenId(new Token.Id(TOKEN_ID));
        token.setTreasuryAccountId(ENTITY_ID);
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
