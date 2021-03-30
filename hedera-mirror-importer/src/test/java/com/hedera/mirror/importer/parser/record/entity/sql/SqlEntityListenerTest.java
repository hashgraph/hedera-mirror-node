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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionSignature;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
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
public class SqlEntityListenerTest extends IntegrationTest {

    private final TransactionRepository transactionRepository;
    private final EntityRepository entityRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final NonFeeTransferRepository nonFeeTransferRepository;
    private final ContractResultRepository contractResultRepository;
    private final LiveHashRepository liveHashRepository;
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

    private final String fileName = "2019-08-30T18_10_00.419072Z.rcd";
    private RecordFile recordFile;

    @BeforeEach
    final void beforeEach() {
        String newFileHash = UUID.randomUUID().toString();
        recordFile = recordFile(1L, fileName, newFileHash, 0L, "fileHash0");

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
    void onCryptoTransferList() throws Exception {
        // given
        CryptoTransfer cryptoTransfer1 = new CryptoTransfer(1L, 1L, EntityId.of(0L, 0L, 1L, ACCOUNT));
        CryptoTransfer cryptoTransfer2 = new CryptoTransfer(2L, -2L, EntityId.of(0L, 0L, 2L, ACCOUNT));

        // when
        sqlEntityListener.onCryptoTransfer(cryptoTransfer1);
        sqlEntityListener.onCryptoTransfer(cryptoTransfer2);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(2, cryptoTransferRepository.count());
        assertExistsAndEquals(cryptoTransferRepository, cryptoTransfer1, cryptoTransfer1.getId());
        assertExistsAndEquals(cryptoTransferRepository, cryptoTransfer2, cryptoTransfer2.getId());
    }

    @Test
    void onNonFeeTransfer() throws Exception {
        // given
        NonFeeTransfer nonFeeTransfer1 = new NonFeeTransfer(1L, new NonFeeTransfer.Id(1L, EntityId
                .of(0L, 0L, 1L, ACCOUNT)));
        NonFeeTransfer nonFeeTransfer2 = new NonFeeTransfer(-2L, new NonFeeTransfer.Id(2L, EntityId
                .of(0L, 0L, 2L, ACCOUNT)));

        // when
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer1);
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer2);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(2, nonFeeTransferRepository.count());
        assertExistsAndEquals(nonFeeTransferRepository, nonFeeTransfer1, nonFeeTransfer1.getId());
        assertExistsAndEquals(nonFeeTransferRepository, nonFeeTransfer2, nonFeeTransfer2.getId());
    }

    @Test
    void onTopicMessage() throws Exception {
        // given
        TopicMessage topicMessage = getTopicMessage();

        // when
        sqlEntityListener.onTopicMessage(topicMessage);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(1, topicMessageRepository.count());
        assertExistsAndEquals(topicMessageRepository, topicMessage, topicMessage.getConsensusTimestamp());
    }

    @Test
    void onFileData() throws Exception {
        // given
        FileData expectedFileData = new FileData(11L, Strings.toByteArray("file data"), EntityId
                .of(0, 0, 111, EntityTypeEnum.FILE), TransactionTypeEnum.CONSENSUSSUBMITMESSAGE.getProtoId());

        // when
        sqlEntityListener.onFileData(expectedFileData);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(1, fileDataRepository.count());
        assertExistsAndEquals(fileDataRepository, expectedFileData, 11L);
    }

    @Test
    void onContractResult() throws Exception {
        // given
        ContractResult expectedContractResult = new ContractResult(15L, "function parameters".getBytes(),
                10000L, "call result".getBytes(), 10000L);

        // when
        sqlEntityListener.onContractResult(expectedContractResult);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(1, contractResultRepository.count());
        assertExistsAndEquals(contractResultRepository, expectedContractResult, 15L);
    }

    @Test
    void onLiveHash() throws Exception {
        // given
        LiveHash expectedLiveHash = new LiveHash(20L, "live hash".getBytes());

        // when
        sqlEntityListener.onLiveHash(expectedLiveHash);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(1, liveHashRepository.count());
        assertExistsAndEquals(liveHashRepository, expectedLiveHash, 20L);
    }

    @Test
    void onTransaction() throws Exception {
        // given
        var expectedTransaction = makeTransaction();

        // when
        sqlEntityListener.onTransaction(expectedTransaction);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(1, transactionRepository.count());
        assertExistsAndEquals(transactionRepository, expectedTransaction, 101L);
    }

    @Test
    void onEntityId() throws Exception {
        // given
        EntityId entityId = EntityId.of(0L, 0L, 10L, ACCOUNT);

        // when
        sqlEntityListener.onEntityId(entityId);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(1, entityRepository.count());
        assertExistsAndEquals(entityRepository, entityId.toEntity(), 10L);
    }

    @Test
    void onEntityIdDuplicates() throws Exception {
        // given
        EntityId entityId = EntityId.of(0L, 0L, 10L, ACCOUNT);

        // when:
        sqlEntityListener.onEntityId(entityId);
        sqlEntityListener.onEntityId(entityId); // duplicate within file
        completeFileAndCommit();

        RecordFile recordFile2 = recordFile(2L, UUID.randomUUID().toString(), null, 1L, null);
        sqlEntityListener.onStart();
        sqlEntityListener.onEntityId(entityId); // duplicate across files
        sqlEntityListener.onEnd(clone(recordFile2));

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile, recordFile2);
        assertEquals(1, entityRepository.count());
        assertExistsAndEquals(entityRepository, entityId.toEntity(), 10L);
    }

    @Test
    void onToken() throws Exception {
        Token token1 = getToken("0.0.3", "0.0.5", 1);
        Token token2 = getToken("0.0.7", "0.0.11", 2);

        // when
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(2, tokenRepository.count());
        assertExistsAndEquals(tokenRepository, token1, token1.getTokenId());
        assertExistsAndEquals(tokenRepository, token2, token2.getTokenId());
    }

    @Test
    void onTokenAccount() throws Exception {
        String tokenId1 = "0.0.3";
        String tokenId2 = "0.0.5";
        String accountId1 = "0.0.7";
        String accountId2 = "0.0.11";
        TokenAccount tokenAccount1 = getTokenAccount(tokenId1, accountId1, 1);
        TokenAccount tokenAccount2 = getTokenAccount(tokenId2, "0.0.11", 2);

        // when
        sqlEntityListener.onTokenAccount(tokenAccount1);
        sqlEntityListener.onTokenAccount(tokenAccount2);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(2, tokenAccountRepository.count());
        assertExistsAndEquals(tokenAccountRepository, tokenAccount1, new TokenAccount.Id(EntityId
                .of(tokenId1, EntityTypeEnum.TOKEN), EntityId.of(accountId1, ACCOUNT)));
        assertExistsAndEquals(tokenAccountRepository, tokenAccount2, new TokenAccount.Id(EntityId
                .of(tokenId2, EntityTypeEnum.TOKEN), EntityId.of(accountId2, ACCOUNT)));
    }

    @Test
    void onTokenTransfer() throws Exception {
        TokenTransfer tokenTransfer1 = getTokenTransfer(1000, 2L, "0.0.3", "0.0.5");
        TokenTransfer tokenTransfer2 = getTokenTransfer(50, 2L, "0.0.7", "0.0.9");
        TokenTransfer tokenTransfer3 = getTokenTransfer(-444, 4L, "0.0.3", "0.0.5");

        // when
        sqlEntityListener.onTokenTransfer(tokenTransfer1);
        sqlEntityListener.onTokenTransfer(tokenTransfer2);
        sqlEntityListener.onTokenTransfer(tokenTransfer3);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(3, tokenTransferRepository.count());
        EntityId tokenId1 = EntityId.of("0.0.3", EntityTypeEnum.TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.7", EntityTypeEnum.TOKEN);
        EntityId accountId1 = EntityId.of("0.0.5", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.9", ACCOUNT);
        assertExistsAndEquals(tokenTransferRepository, tokenTransfer1, new TokenTransfer.Id(2L, tokenId1, accountId1));
        assertExistsAndEquals(tokenTransferRepository, tokenTransfer2, new TokenTransfer.Id(2L, tokenId2, accountId2));
        assertExistsAndEquals(tokenTransferRepository, tokenTransfer3, new TokenTransfer.Id(4L, tokenId1, accountId1));
    }

    @Test
    void onSchedule() throws Exception {
        EntityId entityId1 = EntityId.of("0.0.100", EntityTypeEnum.SCHEDULE);
        EntityId entityId2 = EntityId.of("0.0.200", EntityTypeEnum.SCHEDULE);

        Schedule schedule1 = getSchedule(1, entityId1.entityIdToString());
        Schedule schedule2 = getSchedule(2, entityId2.entityIdToString());

        // when
        sqlEntityListener.onSchedule(schedule1);
        sqlEntityListener.onSchedule(schedule2);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(2, scheduleRepository.count());
        assertExistsAndEquals(scheduleRepository, schedule1, 1L);
        assertExistsAndEquals(scheduleRepository, schedule2, 2L);
    }

    @Test
    void onScheduleSignature() throws Exception {
        EntityId entityId1 = EntityId.of("0.0.100", EntityTypeEnum.SCHEDULE);
        EntityId entityId2 = EntityId.of("0.0.200", EntityTypeEnum.SCHEDULE);
        EntityId entityId3 = EntityId.of("0.0.300", EntityTypeEnum.SCHEDULE);
        byte[] pubKeyPrefix1 = "pubKeyPrefix1".getBytes();
        byte[] pubKeyPrefix2 = "pubKeyPrefix2".getBytes();
        byte[] pubKeyPrefix3 = "pubKeyPrefix3".getBytes();

        TransactionSignature transactionSignature1 = getTransactionSignature(1, entityId1.entityIdToString(), pubKeyPrefix1);
        TransactionSignature transactionSignature2 = getTransactionSignature(2, entityId2.entityIdToString(), pubKeyPrefix2);
        TransactionSignature transactionSignature3 = getTransactionSignature(3, entityId3.entityIdToString(), pubKeyPrefix3);

        // when
        sqlEntityListener.onTransactionSignature(transactionSignature1);
        sqlEntityListener.onTransactionSignature(transactionSignature2);
        sqlEntityListener.onTransactionSignature(transactionSignature3);
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertEquals(3, transactionSignatureRepository.count());
        assertExistsAndEquals(transactionSignatureRepository, transactionSignature1, new TransactionSignature.Id(1L,
                pubKeyPrefix1));
        assertExistsAndEquals(transactionSignatureRepository, transactionSignature2, new TransactionSignature.Id(2L,
                pubKeyPrefix2));
        assertExistsAndEquals(transactionSignatureRepository, transactionSignature3, new TransactionSignature.Id(3L,
                pubKeyPrefix3));
    }

    private <T, ID> void assertExistsAndEquals(CrudRepository<T, ID> repository, T expected, ID id) throws Exception {
        Optional<T> actual = repository.findById(id);
        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }

    private void completeFileAndCommit() {
        sqlEntityListener.onEnd(clone(recordFile));
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

    private Transaction makeTransaction() {
        EntityId entityId = EntityId.of(10, 10, 10, ACCOUNT);
        Transaction transaction = new Transaction();
        transaction.setConsensusNs(101L);
        transaction.setEntityId(entityId);
        transaction.setNodeAccountId(entityId);
        transaction.setMemo("memo".getBytes());
        transaction.setType(14);
        transaction.setResult(22);
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
        topicMessage.setPayerAccountId(EntityId.of("0.1.1000", EntityTypeEnum.ACCOUNT));
        topicMessage.setRealmNum(0);
        topicMessage.setRunningHash("running hash".getBytes());
        topicMessage.setRunningHashVersion(2);
        topicMessage.setSequenceNumber(1L);
        topicMessage.setTopicNum(1001);
        topicMessage.setValidStartTimestamp(4L);

        return topicMessage;
    }

    private Token getToken(String tokenId, String accountId, long createdTimestamp) throws DecoderException {
        var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var hexKey = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(instr))).build().toByteArray();
        Token token = new Token();
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(1000);
        token.setFreezeDefault(false);
        token.setFreezeKey(hexKey);
        token.setInitialSupply(1_000_000_000L);
        token.setKycKey(hexKey);
        token.setModifiedTimestamp(createdTimestamp);
        token.setName("FOO COIN TOKEN");
        token.setSupplyKey(hexKey);
        token.setSymbol("FOOTOK");
        token.setTokenId(new Token.Id(EntityId.of(tokenId, EntityTypeEnum.TOKEN)));
        token.setTreasuryAccountId(EntityId.of(accountId, ACCOUNT));
        token.setWipeKey(hexKey);

        return token;
    }

    private TokenAccount getTokenAccount(String tokenId, String accountId, long createdTimestamp) {
        TokenAccount tokenAccount = new TokenAccount(EntityId
                .of(tokenId, EntityTypeEnum.TOKEN), EntityId.of(accountId, ACCOUNT));
        tokenAccount.setAssociated(true);
        tokenAccount.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        tokenAccount.setModifiedTimestamp(createdTimestamp);

        return tokenAccount;
    }

    private TokenTransfer getTokenTransfer(long amount, long consensusTimestamp, String tokenId, String accountId) {
        TokenTransfer tokenTransfer = new TokenTransfer();
        tokenTransfer.setAmount(amount);
        tokenTransfer
                .setId(new TokenTransfer.Id(consensusTimestamp, EntityId.of(tokenId, EntityTypeEnum.TOKEN), EntityId
                        .of(accountId, ACCOUNT)));

        return tokenTransfer;
    }

    private Schedule getSchedule(long consensusTimestamp, String scheduleId) {
        Schedule schedule = new Schedule();
        schedule.setConsensusTimestamp(consensusTimestamp);
        schedule.setCreatorAccountId(EntityId.of("0.0.123", EntityTypeEnum.ACCOUNT));
        schedule.setPayerAccountId(EntityId.of("0.0.456", EntityTypeEnum.ACCOUNT));
        schedule.setScheduleId(EntityId.of(scheduleId, EntityTypeEnum.SCHEDULE));
        schedule.setTransactionBody("transaction body".getBytes());
        return schedule;
    }

    private TransactionSignature getTransactionSignature(long consensusTimestamp, String scheduleId,
                                                         byte[] pubKeyPrefix) {
        TransactionSignature transactionSignature = new TransactionSignature();
        transactionSignature.setId(new TransactionSignature.Id(
                consensusTimestamp,
                pubKeyPrefix));
        transactionSignature.setEntityId(EntityId.of(scheduleId, EntityTypeEnum.SCHEDULE));
        transactionSignature.setSignature("scheduled transaction signature".getBytes());
        return transactionSignature;
    }

    private RecordFile clone(RecordFile recordFile) {
        return recordFile.toBuilder().build();
    }
}
