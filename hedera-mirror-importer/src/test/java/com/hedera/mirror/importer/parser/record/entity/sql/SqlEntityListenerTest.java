/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity.sql;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.SCHEDULE;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractState;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.NftTransferId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.AssessedCustomFeeRepository;
import com.hedera.mirror.importer.repository.ContractActionRepository;
import com.hedera.mirror.importer.repository.ContractLogRepository;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.ContractStateChangeRepository;
import com.hedera.mirror.importer.repository.ContractStateRepository;
import com.hedera.mirror.importer.repository.CryptoAllowanceRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.EthereumTransactionRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
import com.hedera.mirror.importer.repository.NetworkStakeRepository;
import com.hedera.mirror.importer.repository.NftAllowanceRepository;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.repository.NodeStakeRepository;
import com.hedera.mirror.importer.repository.PrngRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.ScheduleRepository;
import com.hedera.mirror.importer.repository.SidecarFileRepository;
import com.hedera.mirror.importer.repository.StakingRewardTransferRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenAllowanceRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionHashRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.repository.TransactionSignatureRepository;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.Key;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class SqlEntityListenerTest extends IntegrationTest {

    private static final String KEY = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    private static final String KEY2 = "0a3312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    private static final EntityId TRANSACTION_PAYER = EntityId.of("0.0.1000", ACCOUNT);

    private final AssessedCustomFeeRepository assessedCustomFeeRepository;
    private final ContractActionRepository contractActionRepository;
    private final ContractLogRepository contractLogRepository;
    private final ContractRepository contractRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractStateRepository contractStateRepository;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final DomainBuilder domainBuilder;
    private final EntityProperties entityProperties;
    private final EntityRepository entityRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final FileDataRepository fileDataRepository;
    private final LiveHashRepository liveHashRepository;
    private final NetworkStakeRepository networkStakeRepository;
    private final NftRepository nftRepository;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NftTransferRepository nftTransferRepository;
    private final NodeStakeRepository nodeStakeRepository;
    private final PrngRepository prngRepository;
    private final RecordFileRepository recordFileRepository;
    private final SidecarFileRepository sidecarFileRepository;
    private final ScheduleRepository scheduleRepository;
    private final SqlEntityListener sqlEntityListener;
    private final SqlProperties sqlProperties;
    private final StakingRewardTransferRepository stakingRewardTransferRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final TokenRepository tokenRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionHashRepository transactionHashRepository;
    private final TransactionSignatureRepository transactionSignatureRepository;
    private final TransactionTemplate transactionTemplate;

    private Set<TransactionType> defaultTransactionHashTypes;

    private static Key keyFromString(String key) {
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build();
    }

    @BeforeEach
    void beforeEach() {
        defaultTransactionHashTypes = entityProperties.getPersist().getTransactionHashTypes();

        entityProperties.getPersist().setTransactionHash(false);
        entityProperties.getPersist().setTrackBalance(true);
        sqlProperties.setBatchSize(20_000);
        sqlEntityListener.onStart();
    }

    @AfterEach
    void afterEach() {
        entityProperties.getPersist().setTransactionHashTypes(defaultTransactionHashTypes);
    }

    @Test
    void executeBatch() {
        // given
        sqlProperties.setBatchSize(1);
        Entity entity1 = domainBuilder.entity().get();
        Entity entity2 = domainBuilder.entity().get();

        // when
        sqlEntityListener.onEntity(entity1);
        sqlEntityListener.onEntity(entity2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @Test
    void isEnabled() {
        sqlProperties.setEnabled(false);
        assertThat(sqlEntityListener.isEnabled()).isFalse();

        sqlProperties.setEnabled(true);
        assertThat(sqlEntityListener.isEnabled()).isTrue();
    }

    @Test
    void onAssessedCustomFee() {
        // given
        var assessedCustomFee = domainBuilder.assessedCustomFee().get();

        // when
        sqlEntityListener.onAssessedCustomFee(assessedCustomFee);
        completeFileAndCommit();

        // then
        assertThat(assessedCustomFeeRepository.findAll()).containsExactly(assessedCustomFee);
    }

    @Test
    void onContract() {
        // given
        Contract contract1 = domainBuilder.contract().get();
        Contract contract2 = domainBuilder.contract().get();

        // when
        sqlEntityListener.onContract(contract1);
        sqlEntityListener.onContract(contract2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(contractRepository.findAll()).containsExactlyInAnyOrder(contract1, contract2);
    }

    @Test
    void onContractAction() {
        // given
        ContractAction contractAction = domainBuilder.contractAction().get();

        // when
        sqlEntityListener.onContractAction(contractAction);
        completeFileAndCommit();

        // then
        assertThat(contractActionRepository.findAll()).containsExactlyInAnyOrder(contractAction);
    }

    @Test
    void onContractLog() {
        // given
        ContractLog contractLog = domainBuilder.contractLog().get();

        // when
        sqlEntityListener.onContractLog(contractLog);
        completeFileAndCommit();

        // then
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
        assertThat(contractResultRepository.findAll()).containsExactlyInAnyOrder(contractResult);
    }

    @Test
    void onContractStateChange() {
        // given
        ContractStateChange contractStateChange =
                domainBuilder.contractStateChange().get();
        ContractState expectedContractState = ContractState.builder()
                .contractId(contractStateChange.getContractId())
                .createdTimestamp(contractStateChange.getConsensusTimestamp())
                .modifiedTimestamp(contractStateChange.getConsensusTimestamp())
                .slot(DomainUtils.leftPadBytes(contractStateChange.getSlot(), 32))
                .value(contractStateChange.getValueWritten())
                .build();

        // when
        sqlEntityListener.onContractStateChange(contractStateChange);
        completeFileAndCommit();

        // then
        assertThat(contractStateChangeRepository.findAll()).containsExactlyInAnyOrder(contractStateChange);
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrder(expectedContractState);
    }

    @Test
    void onContractState() {
        // given
        var builder = domainBuilder.contractStateChange().customize(c -> c.slot(domainBuilder.bytes(15)));
        var contractStateChangeCreate = builder.get();
        var contractStateChangeValueWritten = builder.customize(c -> c.valueWritten(domainBuilder.bytes(32))
                        .consensusTimestamp(contractStateChangeCreate.getConsensusTimestamp() + 1))
                .get();
        var contractStateChangeNoValue = builder.customize(c ->
                        c.valueWritten(null).consensusTimestamp(contractStateChangeCreate.getConsensusTimestamp() + 2))
                .get();

        // when
        sqlEntityListener.onContractStateChange(contractStateChangeCreate);
        sqlEntityListener.onContractStateChange(contractStateChangeValueWritten);
        sqlEntityListener.onContractStateChange(contractStateChangeNoValue);
        completeFileAndCommit();

        var expectedContractState = ContractState.builder()
                .contractId(contractStateChangeCreate.getContractId())
                .createdTimestamp(contractStateChangeCreate.getConsensusTimestamp())
                .modifiedTimestamp(contractStateChangeValueWritten.getConsensusTimestamp())
                .slot(DomainUtils.leftPadBytes(contractStateChangeCreate.getSlot(), 32))
                .value(contractStateChangeValueWritten.getValueWritten())
                .build();

        // then
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrder(expectedContractState);
    }

    @Test
    void onContractStateMigrateFalse() {
        // given
        var builder = domainBuilder
                .contractStateChange()
                .customize(c -> c.contractId(1000).consensusTimestamp(1L).valueWritten("a".getBytes()));

        var contractStateChange1Create = builder.get();
        var contractStateChange1Update = builder.customize(
                        c -> c.consensusTimestamp(2L).valueWritten("b".getBytes()))
                .get();
        var contractStateChange2Create = builder.customize(
                        c -> c.contractId(1001).consensusTimestamp(2L).valueWritten("c".getBytes()))
                .get();
        var contractStateChange2Update = builder.customize(
                        c -> c.consensusTimestamp(3L).valueWritten(null))
                .get();
        var contractStateChange1Update2 = builder.customize(
                        c -> c.contractId(1000).consensusTimestamp(4L).valueWritten("d".getBytes()))
                .get();
        var contractStateChange2Update2 = builder.customize(
                        c -> c.contractId(1001).consensusTimestamp(4L).valueWritten("e".getBytes()))
                .get();

        // when
        sqlEntityListener.onContractStateChange(contractStateChange1Create);
        sqlEntityListener.onContractStateChange(contractStateChange2Create);
        completeFileAndCommit();

        sqlEntityListener.onContractStateChange(contractStateChange1Update);
        sqlEntityListener.onContractStateChange(contractStateChange2Update);
        completeFileAndCommit();

        sqlEntityListener.onContractStateChange(contractStateChange1Update2);
        sqlEntityListener.onContractStateChange(contractStateChange2Update2);
        completeFileAndCommit();

        var expected = List.of(
                getContractState(contractStateChange1Update2, 1L), getContractState(contractStateChange2Update2, 2L));

        // then
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onContractStateMigrateTrue() {
        // given
        var builder = domainBuilder.contractStateChange().customize(c -> c.contractId(1000)
                .consensusTimestamp(1L)
                .migration(true)
                .slot(new byte[] {1})
                .valueRead("a".getBytes())
                .valueWritten(null));

        var contractStateChange1Create = builder.get();
        var contractStateChange1Update = builder.customize(
                        c -> c.consensusTimestamp(2L).valueWritten("b".getBytes()))
                .get();
        var contractStateChange2Create = builder.customize(
                        c -> c.contractId(1001).consensusTimestamp(2L).valueRead("c".getBytes()))
                .get();
        var contractStateChange2Update = builder.customize(
                        c -> c.consensusTimestamp(3L).valueWritten(null))
                .get();
        var contractStateChange1Update2 = builder.customize(
                        c -> c.contractId(1000).consensusTimestamp(4L).valueRead("d".getBytes()))
                .get();
        var contractStateChange2Update2 = builder.customize(
                        c -> c.contractId(1001).consensusTimestamp(4L).valueRead("e".getBytes()))
                .get();

        // when
        sqlEntityListener.onContractStateChange(contractStateChange1Create);
        sqlEntityListener.onContractStateChange(contractStateChange2Create);
        completeFileAndCommit();

        sqlEntityListener.onContractStateChange(contractStateChange1Update);
        sqlEntityListener.onContractStateChange(contractStateChange2Update);
        completeFileAndCommit();

        sqlEntityListener.onContractStateChange(contractStateChange1Update2);
        sqlEntityListener.onContractStateChange(contractStateChange2Update2);
        completeFileAndCommit();

        var expected = List.of(
                getContractState(contractStateChange1Update2, 1L), getContractState(contractStateChange2Update2, 2L));

        // then
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onCryptoAllowance() {
        // given
        CryptoAllowance cryptoAllowance1 = domainBuilder.cryptoAllowance().get();
        CryptoAllowance cryptoAllowance2 = domainBuilder.cryptoAllowance().get();

        // when
        sqlEntityListener.onCryptoAllowance(cryptoAllowance1);
        sqlEntityListener.onCryptoAllowance(cryptoAllowance2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(cryptoAllowanceRepository.findAll()).containsExactlyInAnyOrder(cryptoAllowance1, cryptoAllowance2);
        assertThat(findHistory(CryptoAllowance.class, "owner, spender")).isEmpty();
    }

    @ValueSource(ints = {1, 2, 3})
    @ParameterizedTest
    void onCryptoAllowanceHistory(int commitIndex) {
        // given
        final String idColumns = "owner, spender";
        var builder = domainBuilder.cryptoAllowance();
        CryptoAllowance cryptoAllowanceCreate = builder.get();

        CryptoAllowance cryptoAllowanceUpdate1 =
                builder.customize(c -> c.amount(999L)).get();
        cryptoAllowanceUpdate1.setTimestampLower(cryptoAllowanceCreate.getTimestampLower() + 1);

        CryptoAllowance cryptoAllowanceUpdate2 =
                builder.customize(c -> c.amount(0L)).get();
        cryptoAllowanceUpdate2.setTimestampLower(cryptoAllowanceCreate.getTimestampLower() + 2);

        // Expected merged objects
        CryptoAllowance mergedCreate = TestUtils.clone(cryptoAllowanceCreate);
        CryptoAllowance mergedUpdate1 = TestUtils.merge(cryptoAllowanceCreate, cryptoAllowanceUpdate1);
        CryptoAllowance mergedUpdate2 = TestUtils.merge(mergedUpdate1, cryptoAllowanceUpdate2);
        mergedCreate.setTimestampUpper(cryptoAllowanceUpdate1.getTimestampLower());

        // when
        sqlEntityListener.onCryptoAllowance(cryptoAllowanceCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(cryptoAllowanceRepository.findAll()).containsExactly(cryptoAllowanceCreate);
            assertThat(findHistory(CryptoAllowance.class, idColumns)).isEmpty();
        }

        sqlEntityListener.onCryptoAllowance(cryptoAllowanceUpdate1);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(cryptoAllowanceRepository.findAll()).containsExactly(mergedUpdate1);
            assertThat(findHistory(CryptoAllowance.class, idColumns)).containsExactly(mergedCreate);
        }

        sqlEntityListener.onCryptoAllowance(cryptoAllowanceUpdate2);
        completeFileAndCommit();

        // then
        mergedUpdate1.setTimestampUpper(cryptoAllowanceUpdate2.getTimestampLower());
        assertThat(cryptoAllowanceRepository.findAll()).containsExactly(mergedUpdate2);
        assertThat(findHistory(CryptoAllowance.class, idColumns)).containsExactly(mergedCreate, mergedUpdate1);
    }

    @Test
    void onCryptoTransfer() {
        // given
        var cryptoTransfer1 = domainBuilder.cryptoTransfer().get();
        var cryptoTransfer2 = domainBuilder.cryptoTransfer().get();

        // when
        sqlEntityListener.onCryptoTransfer(cryptoTransfer1);
        sqlEntityListener.onCryptoTransfer(cryptoTransfer2);
        completeFileAndCommit();

        // then
        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrder(cryptoTransfer1, cryptoTransfer2);
        assertThat(entityRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"true, 95, 225", "false, 100, 200"})
    void onCryptoTransferWhenEntitiesExist(
            boolean trackBalance, long expectedAccountBalance, long expectedContractBalance) {
        // given
        entityProperties.getPersist().setTrackBalance(trackBalance);
        var account = domainBuilder
                .entity()
                .customize(e -> e.balance(100L).type(ACCOUNT))
                .persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.balance(200L).type(CONTRACT))
                .persist();
        var cryptoTransfer1 = domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(-15L).entityId(account.getId()))
                .get();
        var cryptoTransfer2 = domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(10L).entityId(account.getId()))
                .get();
        var cryptoTransfer3 = domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(25L).entityId(contract.getId()))
                .get();
        account.setBalance(expectedAccountBalance);
        contract.setBalance(expectedContractBalance);

        // when
        sqlEntityListener.onCryptoTransfer(cryptoTransfer1);
        sqlEntityListener.onCryptoTransfer(cryptoTransfer2);
        sqlEntityListener.onCryptoTransfer(cryptoTransfer3);
        completeFileAndCommit();

        // then
        assertThat(cryptoTransferRepository.findAll())
                .containsExactlyInAnyOrder(cryptoTransfer1, cryptoTransfer2, cryptoTransfer3);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, contract);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @Test
    void onCryptoTransferBeforeContractCreate() {
        // given
        var contract = domainBuilder
                .entity()
                .customize(e -> e.balance(0L).type(CONTRACT))
                .get();
        var cryptoTransfer = domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(10L)
                        .consensusTimestamp(contract.getCreatedTimestamp() - 1L)
                        .entityId(contract.getId()))
                .get();
        var expectedContract = TestUtils.clone(contract);
        expectedContract.setBalance(10L);

        // when
        sqlEntityListener.onCryptoTransfer(cryptoTransfer);
        sqlEntityListener.onEntity(contract);
        completeFileAndCommit();

        // then
        assertThat(cryptoTransferRepository.findAll()).containsOnly(cryptoTransfer);
        assertThat(entityRepository.findAll()).containsOnly(expectedContract);
    }

    @Test
    void onEndNull() {
        sqlEntityListener.onEnd(null);
        assertThat(recordFileRepository.count()).isZero();
        assertThat(sidecarFileRepository.count()).isZero();
    }

    @Test
    void onEntity() {
        // given
        Entity entity1 = domainBuilder.entity().get();
        Entity entity2 = domainBuilder.entity().get();

        // when
        sqlEntityListener.onEntity(entity1);
        sqlEntityListener.onEntity(entity2);
        completeFileAndCommit();

        // then
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void onEntityCreateAndBalanceChange(int commitIndex) {
        // given
        var entityCreate = domainBuilder.entity().customize(e -> e.balance(0L)).get();
        var balanceUpdate1 =
                Entity.builder().id(entityCreate.getId()).balance(25L).build();
        var balanceUpdate2 =
                Entity.builder().id(entityCreate.getId()).balance(15L).build();
        var expectedEntity = TestUtils.clone(entityCreate);

        // when
        sqlEntityListener.onEntity(entityCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(entityRepository.findAll()).containsExactly(expectedEntity);
            assertThat(findHistory(Entity.class)).isEmpty();
        }

        sqlEntityListener.onEntity(balanceUpdate1);
        expectedEntity.setBalance(25L);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(entityRepository.findAll()).containsExactly(expectedEntity);
            assertThat(findHistory(Entity.class)).isEmpty();
        }

        sqlEntityListener.onEntity(balanceUpdate2);
        expectedEntity.setBalance(40L);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.findAll()).containsExactly(expectedEntity);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @Test
    void onEntityWithNonHistoryUpdates() {
        // given
        // Update to non-history field with existing entity should just update nonce
        Entity existingEntity = domainBuilder.entity().persist();
        Entity existingEntityNonce1 = existingEntity.toEntityId().toEntity();
        existingEntityNonce1.setEthereumNonce(100L);
        existingEntityNonce1.setTimestampRange(null);

        Entity existingEntityNonce2 = existingEntity.toEntityId().toEntity();
        existingEntityNonce2.setEthereumNonce(101L);
        existingEntityNonce2.setTimestampRange(null);

        Entity existingEntityStakePeriodStart1 = existingEntity.toEntityId().toEntity();
        existingEntityStakePeriodStart1.setStakePeriodStart(2L);
        existingEntityStakePeriodStart1.setTimestampRange(null);

        Entity existingEntityStakePeriodStart2 = existingEntity.toEntityId().toEntity();
        existingEntityStakePeriodStart2.setStakePeriodStart(5L);
        existingEntityStakePeriodStart2.setTimestampRange(null);

        // Update to non-history field with partial data should be discarded
        Entity nonExistingEntity = domainBuilder.entity().get();
        Entity nonExistingEntityNonce1 = nonExistingEntity.toEntityId().toEntity();
        nonExistingEntityNonce1.setEthereumNonce(200L);
        nonExistingEntityNonce1.setTimestampRange(null);

        Entity nonExistingEntityNonce2 = nonExistingEntity.toEntityId().toEntity();
        nonExistingEntityNonce2.setEthereumNonce(201L);
        nonExistingEntityNonce2.setTimestampRange(null);

        Entity nonExistingEntityStakePeriodStart1 =
                nonExistingEntity.toEntityId().toEntity();
        nonExistingEntityStakePeriodStart1.setStakePeriodStart(6L);
        nonExistingEntityStakePeriodStart1.setTimestampRange(null);

        Entity nonExistingEntityStakePeriodStart2 =
                nonExistingEntity.toEntityId().toEntity();
        nonExistingEntityStakePeriodStart2.setStakePeriodStart(8L);
        nonExistingEntityStakePeriodStart2.setTimestampRange(null);

        // when
        sqlEntityListener.onEntity(existingEntityNonce1);
        sqlEntityListener.onEntity(existingEntityNonce2);
        sqlEntityListener.onEntity(existingEntityStakePeriodStart1);
        sqlEntityListener.onEntity(existingEntityStakePeriodStart2);
        sqlEntityListener.onEntity(nonExistingEntityNonce1);
        sqlEntityListener.onEntity(nonExistingEntityNonce2);
        sqlEntityListener.onEntity(nonExistingEntityStakePeriodStart1);
        sqlEntityListener.onEntity(nonExistingEntityStakePeriodStart2);
        completeFileAndCommit();

        existingEntity.setEthereumNonce(existingEntityNonce2.getEthereumNonce());
        existingEntity.setStakePeriodStart(existingEntityStakePeriodStart2.getStakePeriodStart());
        assertThat(entityRepository.findAll()).containsExactly(existingEntity);

        Entity existingEntityNonce3 = existingEntity.toEntityId().toEntity();
        existingEntityNonce3.setEthereumNonce(102L);
        existingEntityNonce3.setTimestampRange(null);

        Entity existingEntityStakePeriodStart3 = existingEntity.toEntityId().toEntity();
        existingEntityStakePeriodStart3.setStakePeriodStart(10L);
        existingEntityStakePeriodStart3.setTimestampRange(null);

        Entity nonExistingEntityNonce3 =
                domainBuilder.entityId(EntityType.ACCOUNT).toEntity();
        nonExistingEntityNonce3.setEthereumNonce(202L);
        nonExistingEntityNonce3.setTimestampRange(null);

        Entity nonExistingEntityStakePeriodStart3 =
                domainBuilder.entityId(EntityType.ACCOUNT).toEntity();
        nonExistingEntityStakePeriodStart3.setStakePeriodStart(12L);
        nonExistingEntityStakePeriodStart3.setTimestampRange(null);

        sqlEntityListener.onEntity(existingEntityNonce3);
        sqlEntityListener.onEntity(existingEntityStakePeriodStart3);
        sqlEntityListener.onEntity(nonExistingEntityNonce3);
        sqlEntityListener.onEntity(nonExistingEntityStakePeriodStart3);
        completeFileAndCommit();

        // then
        existingEntity.setEthereumNonce(existingEntityNonce3.getEthereumNonce());
        existingEntity.setStakePeriodStart(existingEntityStakePeriodStart3.getStakePeriodStart());
        assertThat(entityRepository.findAll()).containsExactly(existingEntity);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onEntityWithHistoryAndNonHistoryUpdates(boolean nonHistoryBefore) {
        // given
        Entity entity = domainBuilder.entity().persist();
        Entity entityNonce1 = entity.toEntityId().toEntity();
        entityNonce1.setEthereumNonce(100L);
        entityNonce1.setTimestampRange(null);

        Entity entityStakePeriodStart1 = entity.toEntityId().toEntity();
        entityStakePeriodStart1.setStakePeriodStart(2L);
        entityStakePeriodStart1.setTimestampRange(null);

        Entity entityNonce2 = entity.toEntityId().toEntity();
        entityNonce2.setEthereumNonce(101L);
        entityNonce2.setTimestampRange(null);

        Entity entityStakePeriodStart2 = entity.toEntityId().toEntity();
        entityStakePeriodStart2.setStakePeriodStart(7L);
        entityStakePeriodStart2.setTimestampRange(null);

        Entity entityMemoUpdated = entity.toEntityId().toEntity();
        entityMemoUpdated.setMemo(domainBuilder.text(16));
        entityMemoUpdated.setTimestampLower(domainBuilder.timestamp());

        // when
        if (nonHistoryBefore) {
            sqlEntityListener.onEntity(entityNonce1);
            sqlEntityListener.onEntity(entityNonce2);
            sqlEntityListener.onEntity(entityStakePeriodStart1);
            sqlEntityListener.onEntity(entityStakePeriodStart2);
            sqlEntityListener.onEntity(entityMemoUpdated);
        } else {
            sqlEntityListener.onEntity(entityMemoUpdated);
            sqlEntityListener.onEntity(entityStakePeriodStart1);
            sqlEntityListener.onEntity(entityNonce1);
            sqlEntityListener.onEntity(entityStakePeriodStart2);
            sqlEntityListener.onEntity(entityNonce2);
        }
        completeFileAndCommit();

        // then
        Entity entityMerged = TestUtils.clone(entity);
        entityMerged.setMemo(entityMemoUpdated.getMemo());
        entityMerged.setEthereumNonce(entityNonce2.getEthereumNonce());
        entityMerged.setStakePeriodStart(entityStakePeriodStart2.getStakePeriodStart());
        entityMerged.setTimestampRange(entityMemoUpdated.getTimestampRange());
        entity.setTimestampUpper(entityMemoUpdated.getTimestampLower());

        assertThat(entityRepository.findAll()).containsExactly(entityMerged);
        assertThat(findHistory(Entity.class)).containsExactly(entity);
    }

    @Test
    void onNonHistoryUpdateWithIncorrectTypeThenHistoryUpdate() {
        // given
        var entityId = domainBuilder.entityId(ACCOUNT); // The entity in fact is a contract
        var nonHistoryUpdate = entityId.toEntity();
        nonHistoryUpdate.setStakePeriodStart(120L);
        nonHistoryUpdate.setTimestampRange(null);
        var historyUpdate = entityId.toEntity();
        historyUpdate.setMemo("Update entity memo");
        historyUpdate.setTimestampRange(Range.atLeast(domainBuilder.timestamp()));
        historyUpdate.setType(CONTRACT); // Correct type
        var expectedEntity = TestUtils.clone(historyUpdate);
        expectedEntity.setBalance(0L);
        expectedEntity.setDeclineReward(false);
        expectedEntity.setEthereumNonce(0L);
        expectedEntity.setStakedNodeId(-1L);
        expectedEntity.setStakePeriodStart(120L);

        // when
        sqlEntityListener.onEntity(nonHistoryUpdate);
        sqlEntityListener.onEntity(historyUpdate);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.findAll()).containsOnly(expectedEntity);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void onEntityHistory(int commitIndex) {
        // given
        Entity entityCreate = domainBuilder.entity().get();

        Entity entityUpdate = entityCreate.toEntityId().toEntity();
        entityUpdate.setAlias(entityCreate.getAlias());
        entityUpdate.setAutoRenewAccountId(101L);
        entityUpdate.setAutoRenewPeriod(30L);
        entityUpdate.setDeclineReward(true);
        entityUpdate.setExpirationTimestamp(500L);
        entityUpdate.setKey(domainBuilder.key());
        entityUpdate.setMaxAutomaticTokenAssociations(40);
        entityUpdate.setMemo("updated");
        entityUpdate.setTimestampLower(entityCreate.getTimestampLower() + 1);
        entityUpdate.setProxyAccountId(EntityId.of(100L, ACCOUNT));
        entityUpdate.setReceiverSigRequired(true);
        entityUpdate.setStakedAccountId(domainBuilder.id());
        entityUpdate.setStakedNodeId(-1L);
        entityUpdate.setStakePeriodStart(domainBuilder.id());
        entityUpdate.setSubmitKey(domainBuilder.key());

        Entity entityDelete = entityCreate.toEntityId().toEntity();
        entityDelete.setAlias(entityCreate.getAlias());
        entityDelete.setDeleted(true);
        entityDelete.setTimestampLower(entityCreate.getTimestampLower() + 2);

        // Expected merged objects
        Entity mergedCreate = TestUtils.clone(entityCreate);
        Entity mergedUpdate = TestUtils.merge(entityCreate, entityUpdate);
        Entity mergedDelete = TestUtils.merge(mergedUpdate, entityDelete);
        mergedCreate.setTimestampUpper(entityUpdate.getTimestampLower());

        // when
        sqlEntityListener.onEntity(entityCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(entityRepository.findAll()).containsExactly(entityCreate);
            assertThat(findHistory(Entity.class)).isEmpty();
        }

        sqlEntityListener.onEntity(entityUpdate);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(entityRepository.findAll()).containsExactly(mergedUpdate);
            assertThat(findHistory(Entity.class)).containsExactly(mergedCreate);
        }

        sqlEntityListener.onEntity(entityDelete);
        completeFileAndCommit();

        // then
        mergedUpdate.setTimestampUpper(entityDelete.getTimestampLower());
        assertThat(entityRepository.findAll()).containsExactly(mergedDelete);
        assertThat(findHistory(Entity.class)).containsExactly(mergedCreate, mergedUpdate);
    }

    @Test
    void onNonFeeTransfer() {
        // given
        NonFeeTransfer nonFeeTransfer1 = domainBuilder
                .nonFeeTransfer()
                .customize(n -> n.amount(1L)
                        .consensusTimestamp(1L)
                        .entityId(EntityId.of(1L, ACCOUNT))
                        .payerAccountId(TRANSACTION_PAYER))
                .get();
        NonFeeTransfer nonFeeTransfer2 = domainBuilder
                .nonFeeTransfer()
                .customize(n -> n.amount(2L)
                        .consensusTimestamp(2L)
                        .entityId(EntityId.of(2L, ACCOUNT))
                        .payerAccountId(TRANSACTION_PAYER))
                .get();

        // when
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer1);
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer2);
        completeFileAndCommit();

        // then
        assertThat(findNonFeeTransfers()).containsExactlyInAnyOrder(nonFeeTransfer1, nonFeeTransfer2);
    }

    @Test
    void onTopicMessage() {
        // given
        TopicMessage topicMessage = domainBuilder.topicMessage().get();

        // when
        sqlEntityListener.onTopicMessage(topicMessage);
        completeFileAndCommit();

        // then
        assertThat(topicMessageRepository.findAll()).containsExactlyInAnyOrder(topicMessage);
    }

    @Test
    void onFileData() {
        // given
        var fileData1 = domainBuilder.fileData().get();
        var fileData2 = domainBuilder.fileData().get();

        // when
        sqlEntityListener.onFileData(fileData1);
        sqlEntityListener.onFileData(fileData2);
        completeFileAndCommit();

        // then
        assertThat(fileDataRepository.findAll()).containsExactlyInAnyOrder(fileData1, fileData2);
    }

    @Test
    void onLiveHash() {
        // given
        var liveHash = domainBuilder.liveHash().get();

        // when
        sqlEntityListener.onLiveHash(liveHash);
        completeFileAndCommit();

        // then
        assertThat(liveHashRepository.findAll()).containsExactly(liveHash);
    }

    @Test
    void onNetworkStake() {
        // given
        var networkStake1 = domainBuilder.networkStake().get();
        var networkStake2 = domainBuilder.networkStake().get();

        // when
        sqlEntityListener.onNetworkStake(networkStake1);
        sqlEntityListener.onNetworkStake(networkStake2);
        completeFileAndCommit();

        // then
        assertThat(networkStakeRepository.findAll()).containsExactlyInAnyOrder(networkStake1, networkStake2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onTransaction(boolean persistTransactionHash) {
        // given
        entityProperties.getPersist().setTransactionHash(persistTransactionHash);
        var firstTransaction = domainBuilder.transaction().get();
        var secondTransaction = domainBuilder.transaction().get();
        var thirdTransaction = domainBuilder.transaction().get();
        var expectedTransactionHashes = Stream.of(firstTransaction, secondTransaction, thirdTransaction)
                .filter(t -> persistTransactionHash)
                .map(Transaction::toTransactionHash)
                .toList();

        // when
        sqlEntityListener.onTransaction(firstTransaction);
        sqlEntityListener.onTransaction(secondTransaction);
        sqlEntityListener.onTransaction(thirdTransaction);
        completeFileAndCommit();

        // then
        assertThat(transactionRepository.findAll())
                .containsExactlyInAnyOrder(firstTransaction, secondTransaction, thirdTransaction);

        assertThat(transactionRepository.findById(firstTransaction.getConsensusTimestamp()))
                .get()
                .isNotNull()
                .extracting(Transaction::getIndex)
                .isEqualTo(0);

        assertThat(transactionRepository.findById(secondTransaction.getConsensusTimestamp()))
                .get()
                .isNotNull()
                .extracting(Transaction::getIndex)
                .isEqualTo(1);

        assertThat(transactionRepository.findById(thirdTransaction.getConsensusTimestamp()))
                .get()
                .isNotNull()
                .extracting(Transaction::getIndex)
                .isEqualTo(2);

        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTransactionHashes);
    }

    @ParameterizedTest
    @EnumSource(
            value = TransactionType.class,
            names = {"CRYPTOTRANSFER", "CONSENSUSSUBMITMESSAGE"})
    void onTransactionHashByTransactionType(TransactionType includedTransactionType) {
        // given
        entityProperties.getPersist().setTransactionHash(true);
        entityProperties.getPersist().setTransactionHashTypes(Set.of(includedTransactionType));
        var consensusSubmitMessage = domainBuilder
                .transaction()
                .customize(t -> t.type(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId()))
                .get();
        var cryptoTransfer = domainBuilder.transaction().get();
        var expectedTransactionHashes = Stream.of(consensusSubmitMessage, cryptoTransfer)
                .filter(t -> t.getType() == includedTransactionType.getProtoId())
                .map(Transaction::toTransactionHash)
                .toList();

        // when
        sqlEntityListener.onTransaction(cryptoTransfer);
        sqlEntityListener.onTransaction(consensusSubmitMessage);
        completeFileAndCommit();

        // then
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrder(consensusSubmitMessage, cryptoTransfer);
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTransactionHashes);
    }

    @Test
    void onTransactionHashWhenFilterEmpty() {
        // given
        entityProperties.getPersist().setTransactionHash(true);
        entityProperties.getPersist().setTransactionHashTypes(Collections.emptySet());
        var consensusSubmitMessage = domainBuilder
                .transaction()
                .customize(t -> t.type(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId()))
                .get();
        var cryptoTransfer = domainBuilder.transaction().get();
        var expectedTransactionHashes = Stream.of(consensusSubmitMessage, cryptoTransfer)
                .map(Transaction::toTransactionHash)
                .toList();

        // when
        sqlEntityListener.onTransaction(cryptoTransfer);
        sqlEntityListener.onTransaction(consensusSubmitMessage);
        completeFileAndCommit();

        // then
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrder(consensusSubmitMessage, cryptoTransfer);
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTransactionHashes);
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

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftAllowance() {
        // given
        NftAllowance nftAllowance1 = domainBuilder.nftAllowance().get();
        NftAllowance nftAllowance2 = domainBuilder.nftAllowance().get();

        // when
        sqlEntityListener.onNftAllowance(nftAllowance1);
        sqlEntityListener.onNftAllowance(nftAllowance2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(nftAllowanceRepository.findAll()).containsExactlyInAnyOrder(nftAllowance1, nftAllowance2);
        assertThat(findHistory(NftAllowance.class, "payer_account_id, spender, token_id"))
                .isEmpty();
    }

    @ValueSource(ints = {1, 2})
    @ParameterizedTest
    void onNftAllowanceHistory(int commitIndex) {
        // given
        final String idColumns = "payer_account_id, spender, token_id";
        var builder = domainBuilder.nftAllowance();
        NftAllowance nftAllowanceCreate =
                builder.customize(c -> c.approvedForAll(true)).get();

        NftAllowance nftAllowanceUpdate1 = builder.get();
        nftAllowanceUpdate1.setTimestampLower(nftAllowanceCreate.getTimestampLower() + 1);

        // Expected merged objects
        NftAllowance mergedCreate = TestUtils.clone(nftAllowanceCreate);
        NftAllowance mergedUpdate1 = TestUtils.merge(nftAllowanceCreate, nftAllowanceUpdate1);
        mergedCreate.setTimestampUpper(nftAllowanceUpdate1.getTimestampLower());

        // when
        sqlEntityListener.onNftAllowance(nftAllowanceCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(nftAllowanceRepository.findAll()).containsExactly(nftAllowanceCreate);
            assertThat(findHistory(NftAllowance.class, idColumns)).isEmpty();
        }

        sqlEntityListener.onNftAllowance(nftAllowanceUpdate1);
        completeFileAndCommit();

        // then
        assertThat(nftAllowanceRepository.findAll()).containsExactly(mergedUpdate1);
        assertThat(findHistory(NftAllowance.class, idColumns)).containsExactly(mergedCreate);
    }

    @ValueSource(ints = {1, 2})
    @ParameterizedTest
    void onNftWithInstanceAllowance(int commitIndex) {
        // given
        var nft = domainBuilder.nft().persist();

        // grant allowance
        var expectedNft = TestUtils.clone(nft);
        expectedNft.setDelegatingSpender(domainBuilder.entityId(ACCOUNT));
        expectedNft.setModifiedTimestamp(domainBuilder.timestamp());
        expectedNft.setSpender(domainBuilder.entityId(ACCOUNT));

        var nftUpdate = TestUtils.clone(expectedNft);
        nftUpdate.setCreatedTimestamp(null);

        sqlEntityListener.onNft(nftUpdate);
        if (commitIndex > 1) {
            // when
            completeFileAndCommit();
            // then
            assertThat(nftRepository.findAll()).containsOnly(expectedNft);
        }

        // revoke allowance
        expectedNft = TestUtils.clone(nft);
        expectedNft.setModifiedTimestamp(domainBuilder.timestamp());

        nftUpdate = TestUtils.clone(expectedNft);
        nftUpdate.setCreatedTimestamp(null);
        sqlEntityListener.onNft(nftUpdate);

        // when
        completeFileAndCommit();

        // then
        assertThat(nftRepository.findAll()).containsOnly(expectedNft);
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

        // expected nfts
        Nft nft1 = getNft(tokenId1, 1L, null, 3L, true, metadata1, 5L); // transfer
        Nft nft2 = getNft(tokenId1, 2L, null, 4L, true, metadata2, 6L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onNftTransfer(boolean trackBalance) {
        entityProperties.getPersist().setTrackBalance(trackBalance);
        var nftTransfer1 = domainBuilder.nftTransfer().get();
        var nftTransfer2 = domainBuilder.nftTransfer().get();
        var nftTransfer3 = domainBuilder.nftTransfer().get();

        var expectedTransfers = List.of(nftTransfer1, nftTransfer2, nftTransfer3);
        // token account upsert needs token class in db
        expectedTransfers.forEach(transfer -> {
            var tokenId = new TokenId(transfer.getId().getTokenId());
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenId).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                    .persist();
        });
        var expectedTokenAccounts = expectedTransfers.stream()
                .flatMap(transfer -> {
                    long sender = transfer.getSenderAccountId().getId();
                    long receiver = transfer.getReceiverAccountId().getId();
                    long tokenId = transfer.getId().getTokenId().getId();
                    var tokenAccountSender = domainBuilder
                            .tokenAccount()
                            .customize(
                                    ta -> ta.accountId(sender).tokenId(tokenId).balance(6))
                            .persist();
                    var tokenAccountReceiver = domainBuilder
                            .tokenAccount()
                            .customize(ta ->
                                    ta.accountId(receiver).tokenId(tokenId).balance(1))
                            .persist();
                    if (trackBalance) {
                        tokenAccountSender.setBalance(5);
                        tokenAccountReceiver.setBalance(2);
                    }

                    return Stream.of(tokenAccountSender, tokenAccountReceiver);
                })
                .toList();

        // when
        sqlEntityListener.onNftTransfer(nftTransfer1);
        sqlEntityListener.onNftTransfer(nftTransfer2);
        sqlEntityListener.onNftTransfer(nftTransfer3);
        completeFileAndCommit();

        // then
        assertThat(nftTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTransfers);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenAccounts);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onNftTransferBurn(boolean trackBalance) {
        // given
        entityProperties.getPersist().setTrackBalance(trackBalance);
        var token = domainBuilder
                .token()
                .customize(t -> t.type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        var tokenId = token.getTokenId().getTokenId();
        var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.balance(2L).tokenId(tokenId.getId()))
                .persist();
        var nftTransferId = new NftTransferId(domainBuilder.timestamp(), 1L, tokenId);
        var nftTransfer = domainBuilder
                .nftTransfer()
                .customize(t -> t.id(nftTransferId)
                        .receiverAccountId(EntityId.EMPTY)
                        .senderAccountId(EntityId.of(tokenAccount.getAccountId(), ACCOUNT)))
                .get();

        // when
        sqlEntityListener.onNftTransfer(nftTransfer);
        completeFileAndCommit();

        // then
        nftTransfer.setReceiverAccountId(null);
        if (trackBalance) {
            tokenAccount.setBalance(tokenAccount.getBalance() - 1);
        }
        assertThat(nftTransferRepository.findAll()).containsExactly(nftTransfer);
        assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccount);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onNftTransferMint(boolean trackBalance) {
        // given
        entityProperties.getPersist().setTrackBalance(trackBalance);
        var token = domainBuilder
                .token()
                .customize(t -> t.type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        var tokenId = token.getTokenId().getTokenId();
        var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenId.getId()))
                .persist();
        var nftTransferId = new NftTransferId(domainBuilder.timestamp(), 1L, tokenId);
        var nftTransfer = domainBuilder
                .nftTransfer()
                .customize(t -> t.id(nftTransferId)
                        .receiverAccountId(EntityId.of(tokenAccount.getAccountId(), ACCOUNT))
                        .senderAccountId(EntityId.EMPTY))
                .get();

        // when
        sqlEntityListener.onNftTransfer(nftTransfer);
        completeFileAndCommit();

        // then
        nftTransfer.setSenderAccountId(null);
        if (trackBalance) {
            tokenAccount.setBalance(tokenAccount.getBalance() + 1);
        }
        assertThat(nftTransferRepository.findAll()).containsExactly(nftTransfer);
        assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccount);
    }

    @Test
    void onNftTransferMultiReceiverSingleTimestamp() {
        var nftTransfer = domainBuilder.nftTransfer();
        EntityId entity1 = EntityId.of("0.0.10", ACCOUNT);
        EntityId entity2 = EntityId.of("0.0.11", ACCOUNT);
        EntityId entity3 = EntityId.of("0.0.12", ACCOUNT);
        EntityId entity4 = EntityId.of("0.0.13", ACCOUNT);
        var nftTransfer1 = nftTransfer
                .customize(n -> n.senderAccountId(entity1).receiverAccountId(entity2))
                .get();
        var nftTransfer2 = nftTransfer
                .customize(n -> n.senderAccountId(entity2).receiverAccountId(entity3))
                .get();
        var nftTransfer3 = nftTransfer
                .customize(n -> n.senderAccountId(entity3).receiverAccountId(entity4))
                .get();

        // when
        sqlEntityListener.onNftTransfer(nftTransfer1);
        sqlEntityListener.onNftTransfer(nftTransfer2);
        sqlEntityListener.onNftTransfer(nftTransfer3);
        completeFileAndCommit();

        // then
        var mergedNftTransfer = nftTransfer
                .customize(n -> n.senderAccountId(entity1).receiverAccountId(entity4))
                .get();
        assertThat(nftTransferRepository.findAll()).containsExactlyInAnyOrder(mergedNftTransfer);
    }

    @Test
    void onNftTransferDuplicates() {
        NftTransfer nftTransfer1 = domainBuilder.nftTransfer().get();

        // when
        sqlEntityListener.onNftTransfer(nftTransfer1);
        sqlEntityListener.onNftTransfer(nftTransfer1);
        sqlEntityListener.onNftTransfer(nftTransfer1);
        completeFileAndCommit();

        // then
        assertThat(nftTransferRepository.findAll()).containsExactlyInAnyOrder(nftTransfer1);
    }

    @Test
    void onNodeStake() {
        // given
        var nodeStake1 = domainBuilder.nodeStake().get();
        var nodeStake2 = domainBuilder.nodeStake().get();

        // when
        sqlEntityListener.onNodeStake(nodeStake1);
        sqlEntityListener.onNodeStake(nodeStake2);
        completeFileAndCommit();

        // then
        assertThat(nodeStakeRepository.findAll()).containsExactlyInAnyOrder(nodeStake1, nodeStake2);
    }

    @Test
    void onPrng() {
        var prng = domainBuilder.prng().get();
        var prng2 = domainBuilder
                .prng()
                .customize(r -> r.range(0).prngNumber(null).prngBytes(domainBuilder.bytes(382)))
                .get();

        sqlEntityListener.onPrng(prng);
        sqlEntityListener.onPrng(prng2);

        // when
        completeFileAndCommit();

        // then
        assertThat(prngRepository.findAll()).containsExactlyInAnyOrder(prng, prng2);
    }

    @Test
    void onStakingRewardTransfer() {
        // given
        var transfer1 = domainBuilder.stakingRewardTransfer().get();
        var transfer2 = domainBuilder.stakingRewardTransfer().get();
        var transfer3 = domainBuilder.stakingRewardTransfer().get();

        // when
        sqlEntityListener.onStakingRewardTransfer(transfer1);
        sqlEntityListener.onStakingRewardTransfer(transfer2);
        sqlEntityListener.onStakingRewardTransfer(transfer3);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(stakingRewardTransferRepository.findAll())
                .containsExactlyInAnyOrder(transfer1, transfer2, transfer3);
    }

    @Test
    void onStakingRewardTransferWithExistingEntity() {
        // given
        var account = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(1L))
                .persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.type(CONTRACT).stakedNodeId(0L).stakePeriodStart(1L))
                .persist();
        var transfer1 = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(account.getId()).amount(10L))
                .get();
        var transfer2 = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(contract.getId()).amount(20L))
                .get();

        // when
        sqlEntityListener.onStakingRewardTransfer(transfer1);
        sqlEntityListener.onStakingRewardTransfer(transfer2);
        completeFileAndCommit();

        // then
        account.setStakePeriodStart(Utility.getEpochDay(transfer1.getConsensusTimestamp()) - 1);
        contract.setStakePeriodStart(Utility.getEpochDay(transfer2.getConsensusTimestamp()) - 1);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, contract);
        assertThat(stakingRewardTransferRepository.findAll()).containsExactlyInAnyOrder(transfer1, transfer2);
    }

    @Test
    void onStakingRewardTransferAfterMemoUpdate() {
        // given
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(1L))
                .persist();
        long updateTimestamp = domainBuilder.timestamp();
        long stakingRewardTransferTimestamp = updateTimestamp + 1;
        var entityMemoUpdate = Entity.builder()
                .id(entity.getId())
                .memo(domainBuilder.text(6))
                .timestampRange(Range.atLeast(updateTimestamp))
                .type(entity.getType())
                .build();
        // staking reward transfer after entity memo update
        var transfer = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(stakingRewardTransferTimestamp))
                .get();

        // when
        sqlEntityListener.onEntity(entityMemoUpdate);
        sqlEntityListener.onStakingRewardTransfer(transfer);
        completeFileAndCommit();

        // then
        entity.setMemo(entityMemoUpdate.getMemo());
        entity.setStakePeriodStart(Utility.getEpochDay(stakingRewardTransferTimestamp) - 1);
        entity.setTimestampRange(Range.atLeast(updateTimestamp));
        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(stakingRewardTransferRepository.findAll()).containsExactly(transfer);
    }

    @Test
    void onStakingRewardTransferFromStakingUpdate() {
        // given
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(1L))
                .persist();
        long timestamp = domainBuilder.timestamp();
        var entityStakingUpdate = Entity.builder()
                .id(entity.getId())
                .stakedNodeId(1L)
                .stakePeriodStart(20L)
                .timestampRange(Range.atLeast(timestamp))
                .type(entity.getType())
                .build();
        var transfer = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(timestamp))
                .get();

        // when
        sqlEntityListener.onEntity(entityStakingUpdate);
        sqlEntityListener.onStakingRewardTransfer(transfer);
        completeFileAndCommit();

        // then
        entity.setStakedNodeId(1L);
        entity.setStakePeriodStart(20L);
        entity.setTimestampRange(Range.atLeast(timestamp));
        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(stakingRewardTransferRepository.findAll()).containsExactly(transfer);
    }

    @Test
    void onStakingRewardTransferFromMemoUpdate() {
        // given
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(1L))
                .persist();
        long timestamp = domainBuilder.timestamp();
        var entityMemoUpdate = Entity.builder()
                .id(entity.getId())
                .memo(domainBuilder.text(6))
                .timestampRange(Range.atLeast(timestamp))
                .type(entity.getType())
                .build();
        var transfer = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(timestamp))
                .get();

        // when
        sqlEntityListener.onEntity(entityMemoUpdate);
        sqlEntityListener.onStakingRewardTransfer(transfer);
        completeFileAndCommit();

        // then
        entity.setMemo(entityMemoUpdate.getMemo());
        entity.setStakePeriodStart(Utility.getEpochDay(timestamp) - 1);
        entity.setTimestampRange(Range.atLeast(timestamp));
        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(stakingRewardTransferRepository.findAll()).containsExactly(transfer);
    }

    @Test
    void onStakingRewardTransferAfterNonHistoryUpdate() {
        // given
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(1L).type(CONTRACT))
                .persist();
        var entityBalanceUpdate =
                Entity.builder().id(entity.getId()).balance(200L).build();
        var transfer = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()))
                .get();

        // when
        sqlEntityListener.onEntity(entityBalanceUpdate);
        sqlEntityListener.onStakingRewardTransfer(transfer);
        completeFileAndCommit();

        // then
        entity.setBalance(entity.getBalance() + entityBalanceUpdate.getBalance());
        entity.setStakePeriodStart(Utility.getEpochDay(transfer.getConsensusTimestamp()) - 1);
        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(stakingRewardTransferRepository.findAll()).containsExactly(transfer);
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
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(token1, token2);
    }

    @Test
    void onTokenMerge() {
        EntityId tokenId = EntityId.of("0.0.3", TOKEN);
        EntityId accountId = EntityId.of("0.0.500", ACCOUNT);

        // save token entities first
        Token token = getToken(
                tokenId,
                accountId,
                1L,
                1L,
                1000,
                false,
                keyFromString(KEY),
                1_000_000_000L,
                null,
                "FOO COIN TOKEN",
                null,
                "FOOTOK",
                null,
                null,
                TokenPauseStatusEnum.UNPAUSED);
        sqlEntityListener.onToken(token);

        Token tokenUpdated = getToken(
                tokenId,
                accountId,
                null,
                5L,
                null,
                null,
                null,
                null,
                keyFromString(KEY2),
                "BAR COIN TOKEN",
                keyFromString(KEY),
                "BARTOK",
                keyFromString(KEY2),
                keyFromString(KEY2),
                TokenPauseStatusEnum.UNPAUSED);
        sqlEntityListener.onToken(tokenUpdated);
        completeFileAndCommit();

        // then
        Token tokenMerged = getToken(
                tokenId,
                accountId,
                1L,
                5L,
                1000,
                false,
                keyFromString(KEY),
                1_000_000_000L,
                keyFromString(KEY2),
                "BAR COIN TOKEN",
                keyFromString(KEY),
                "BARTOK",
                keyFromString(KEY2),
                keyFromString(KEY2),
                TokenPauseStatusEnum.UNPAUSED);
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

        completeFileAndCommit();

        // then
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
        TokenAccount tokenAccount1 = getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                Range.atLeast(5L));
        TokenAccount tokenAccount2 = getTokenAccount(
                tokenId2,
                accountId2,
                6L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                Range.atLeast(6L));

        // when
        sqlEntityListener.onTokenAccount(tokenAccount1);
        sqlEntityListener.onTokenAccount(TestUtils.clone(tokenAccount1));
        sqlEntityListener.onTokenAccount(tokenAccount2);
        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(tokenAccount1, tokenAccount2);
        assertThat(findTokenAccountHistory()).isEmpty();
    }

    @Test
    void onTokenAccountDissociate() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);

        // save token entities first
        Token token1 = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        sqlEntityListener.onToken(token1);
        completeFileAndCommit();

        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);
        TokenAccount associate = getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                Range.atLeast(5L));
        TokenAccount dissociate =
                getTokenAccount(tokenId1, accountId1, null, false, null, 0, null, null, Range.atLeast(10L));

        // when
        sqlEntityListener.onTokenAccount(associate);
        sqlEntityListener.onTokenAccount(dissociate);
        sqlEntityListener.onTokenAccount(TestUtils.clone(dissociate));
        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll())
                .containsExactly(getTokenAccount(
                        tokenId1,
                        accountId1,
                        5L,
                        false,
                        false,
                        0,
                        TokenFreezeStatusEnum.NOT_APPLICABLE,
                        TokenKycStatusEnum.NOT_APPLICABLE,
                        Range.atLeast(10L)));
        assertThat(findTokenAccountHistory()).containsExactly(associate);
    }

    @Test
    void onTokenAccountMerge() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);

        // save token entities first
        Token token = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        sqlEntityListener.onToken(token);

        // when
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);
        TokenAccount tokenAccountAssociate =
                getTokenAccount(tokenId1, accountId1, 5L, true, false, 0, null, null, Range.atLeast(5L));
        sqlEntityListener.onTokenAccount(tokenAccountAssociate);
        var expectedTokenAccountAssociate = getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED,
                Range.closedOpen(5L, 15L));

        TokenAccount tokenAccountKyc = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, TokenKycStatusEnum.GRANTED, Range.atLeast(15L));
        sqlEntityListener.onTokenAccount(tokenAccountKyc);

        completeFileAndCommit();

        // then
        TokenAccount tokenAccountMerged = getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.atLeast(15L));
        assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccountMerged);
        assertThat(findTokenAccountHistory()).containsExactly(expectedTokenAccountAssociate);
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
        TokenAccount associate = getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.REVOKED,
                Range.atLeast(5L));
        tokenAccountRepository.save(associate);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.REVOKED,
                Range.closedOpen(5L, 6L)));

        // when
        TokenAccount freeze = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, TokenFreezeStatusEnum.FROZEN, null, Range.atLeast(6L));
        sqlEntityListener.onTokenAccount(freeze);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.REVOKED,
                Range.closedOpen(6L, 7L)));

        TokenAccount kycGrant = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, TokenKycStatusEnum.GRANTED, Range.atLeast(7L));
        sqlEntityListener.onTokenAccount(kycGrant);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(7L, 8L)));

        TokenAccount dissociate =
                getTokenAccount(tokenId1, accountId1, null, false, null, 0, null, null, Range.atLeast(8L));
        sqlEntityListener.onTokenAccount(dissociate);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                false,
                false,
                0,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(8L, 20L)));

        // associate after dissociate, the token has freeze key with freezeDefault = false, the token also has kyc key,
        // so the new relationship should have UNFROZEN, REVOKED
        TokenAccount reassociate =
                getTokenAccount(tokenId1, accountId1, 20L, true, false, 0, null, null, Range.atLeast(20L));
        sqlEntityListener.onTokenAccount(reassociate);

        var expectedToken = getTokenAccount(
                tokenId1,
                accountId1,
                20L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED,
                Range.atLeast(20L));
        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactly(expectedToken);
        assertThat(findTokenAccountHistory()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onTokenAccountMissingToken() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);

        // given no token row in db

        // when
        var associate = getTokenAccount(tokenId1, accountId1, 10L, true, false, 0, null, null, Range.atLeast(10L));
        sqlEntityListener.onTokenAccount(associate);

        var kycGrant = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, TokenKycStatusEnum.GRANTED, Range.atLeast(11L));
        sqlEntityListener.onTokenAccount(kycGrant);

        completeFileAndCommit();

        // then
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
        TokenAccount freeze = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, TokenFreezeStatusEnum.FROZEN, null, Range.atLeast(10L));
        sqlEntityListener.onTokenAccount(freeze);

        TokenAccount kycGrant = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, TokenKycStatusEnum.GRANTED, Range.atLeast(15L));
        sqlEntityListener.onTokenAccount(kycGrant);

        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll()).isEmpty();
        assertThat(findTokenAccountHistory()).isEmpty();
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
        TokenAccount associate =
                getTokenAccount(tokenId1, accountId1, 5L, true, false, 0, null, null, Range.atLeast(5L));
        sqlEntityListener.onTokenAccount(associate);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED,
                Range.closedOpen(5L, 10L)));

        completeFileAndCommit();

        // when in the next record file we have freeze, kycGrant, dissociate, associate, kycGrant
        TokenAccount freeze = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, TokenFreezeStatusEnum.FROZEN, null, Range.atLeast(10L));
        sqlEntityListener.onTokenAccount(freeze);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.REVOKED,
                Range.closedOpen(10L, 12L)));

        TokenAccount kycGrant = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, TokenKycStatusEnum.GRANTED, Range.atLeast(12L));
        sqlEntityListener.onTokenAccount(kycGrant);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(12L, 15L)));

        TokenAccount dissociate =
                getTokenAccount(tokenId1, accountId1, null, false, null, 0, null, null, Range.atLeast(15L));
        sqlEntityListener.onTokenAccount(dissociate);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                false,
                false,
                0,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(15L, 20L)));

        associate = getTokenAccount(tokenId1, accountId1, 20L, true, true, 0, null, null, Range.atLeast(20L));
        sqlEntityListener.onTokenAccount(associate);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                20L,
                true,
                true,
                0,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED,
                Range.closedOpen(20L, 22L)));

        kycGrant = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, TokenKycStatusEnum.GRANTED, Range.atLeast(22L));
        sqlEntityListener.onTokenAccount(kycGrant);

        completeFileAndCommit();

        var expectedTokenAccount = getTokenAccount(
                tokenId1,
                accountId1,
                20L,
                true,
                true,
                0,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.atLeast(22L));

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactly(expectedTokenAccount);
        assertThat(findTokenAccountHistory()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onTokenAllowance() {
        // given
        TokenAllowance tokenAllowance1 = domainBuilder.tokenAllowance().get();
        TokenAllowance tokenAllowance2 = domainBuilder.tokenAllowance().get();

        // when
        sqlEntityListener.onTokenAllowance(tokenAllowance1);
        sqlEntityListener.onTokenAllowance(tokenAllowance2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrder(tokenAllowance1, tokenAllowance2);
        assertThat(findHistory(TokenAllowance.class, "payer_account_id, spender, token_id"))
                .isEmpty();
    }

    @ValueSource(ints = {1, 2, 3})
    @ParameterizedTest
    void onTokenAllowanceHistory(int commitIndex) {
        // given
        final String idColumns = "payer_account_id, spender, token_id";
        var builder = domainBuilder.tokenAllowance();
        TokenAllowance tokenAllowanceCreate = builder.get();

        TokenAllowance tokenAllowanceUpdate1 =
                builder.customize(c -> c.amount(999L)).get();
        tokenAllowanceUpdate1.setTimestampLower(tokenAllowanceCreate.getTimestampLower() + 1);

        TokenAllowance tokenAllowanceUpdate2 =
                builder.customize(c -> c.amount(0)).get();
        tokenAllowanceUpdate2.setTimestampLower(tokenAllowanceCreate.getTimestampLower() + 2);

        // Expected merged objects
        TokenAllowance mergedCreate = TestUtils.clone(tokenAllowanceCreate);
        TokenAllowance mergedUpdate1 = TestUtils.merge(tokenAllowanceCreate, tokenAllowanceUpdate1);
        TokenAllowance mergedUpdate2 = TestUtils.merge(mergedUpdate1, tokenAllowanceUpdate2);
        mergedCreate.setTimestampUpper(tokenAllowanceUpdate1.getTimestampLower());

        // when
        sqlEntityListener.onTokenAllowance(tokenAllowanceCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowanceCreate);
            assertThat(findHistory(TokenAllowance.class, idColumns)).isEmpty();
        }

        sqlEntityListener.onTokenAllowance(tokenAllowanceUpdate1);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(tokenAllowanceRepository.findAll()).containsExactly(mergedUpdate1);
            assertThat(findHistory(TokenAllowance.class, idColumns)).containsExactly(mergedCreate);
        }

        sqlEntityListener.onTokenAllowance(tokenAllowanceUpdate2);
        completeFileAndCommit();

        // then
        mergedUpdate1.setTimestampUpper(tokenAllowanceUpdate2.getTimestampLower());
        assertThat(tokenAllowanceRepository.findAll()).containsExactly(mergedUpdate2);
        assertThat(findHistory(TokenAllowance.class, idColumns)).containsExactly(mergedCreate, mergedUpdate1);
    }

    @Test
    void onTokenTransfer() {
        TokenTransfer tokenTransfer1 = domainBuilder.tokenTransfer().get();
        TokenTransfer tokenTransfer2 = domainBuilder.tokenTransfer().get();
        TokenTransfer tokenTransfer3 = domainBuilder.tokenTransfer().get();

        // when
        sqlEntityListener.onTokenTransfer(tokenTransfer1);
        sqlEntityListener.onTokenTransfer(tokenTransfer2);
        sqlEntityListener.onTokenTransfer(tokenTransfer3);
        completeFileAndCommit();

        // then
        assertThat(tokenTransferRepository.findAll())
                .containsExactlyInAnyOrder(tokenTransfer1, tokenTransfer2, tokenTransfer3);
    }

    @ValueSource(ints = {1, 2, 3, 4, 5})
    @ParameterizedTest
    void onTokenTransferTokenAccountBalance(int commitIndex) {
        // given
        var tokenId1 = EntityId.of("0.0.3", TOKEN);

        // save token entities first
        var token1 = domainBuilder
                .token()
                .customize(c -> c.createdTimestamp(1L)
                        .modifiedTimestamp(1L)
                        .tokenId(new TokenId(tokenId1))
                        .totalSupply(1_000_000_000L)
                        .treasuryAccountId(EntityId.of("0.0.500", ACCOUNT))
                        .type(TokenTypeEnum.FUNGIBLE_COMMON))
                .get();

        sqlEntityListener.onToken(token1);
        completeFileAndCommit();

        var accountId1 = EntityId.of("0.0.7", ACCOUNT);
        var tokenAccount = getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                Range.atLeast(5L));

        // when
        sqlEntityListener.onTokenAccount(tokenAccount);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccount);
            assertThat(findTokenAccountHistory()).isEmpty();
            assertThat(tokenTransferRepository.findAll()).isEmpty();
        }

        var tokenTransferId = new TokenTransfer.Id();
        tokenTransferId.setAccountId(EntityId.of(tokenAccount.getAccountId(), ACCOUNT));
        tokenTransferId.setTokenId(EntityId.of(tokenAccount.getTokenId(), TOKEN));
        tokenTransferId.setConsensusTimestamp(tokenAccount.getCreatedTimestamp() + 1);
        TokenTransfer tokenTransfer1 = domainBuilder
                .tokenTransfer()
                .customize(t -> t.id(tokenTransferId))
                .get();
        var expected = getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                tokenTransfer1.getAmount(),
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                Range.atLeast(5L));

        sqlEntityListener.onTokenTransfer(tokenTransfer1);
        if (commitIndex > 2) {
            completeFileAndCommit();
            tokenAccount.setBalance(tokenTransfer1.getAmount());
            assertThat(tokenAccountRepository.findAll()).containsExactly(expected);
            assertThat(findTokenAccountHistory()).isEmpty();
            assertThat(tokenTransferRepository.findAll()).containsExactly(tokenTransfer1);
        }

        TokenAccount freeze = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, TokenFreezeStatusEnum.FROZEN, null, Range.atLeast(6L));
        expected.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        expected.setTimestampRange(Range.atLeast(6L));
        tokenAccount.setTimestampRange(Range.closedOpen(5L, 6L));

        sqlEntityListener.onTokenAccount(freeze);
        if (commitIndex > 3) {
            completeFileAndCommit();
            assertThat(tokenAccountRepository.findAll()).containsExactly(expected);
            assertThat(findTokenAccountHistory()).containsExactly(tokenAccount);
            assertThat(tokenTransferRepository.findAll()).containsExactly(tokenTransfer1);
        }

        var tokenTransferId2 = new TokenTransfer.Id();
        tokenTransferId2.setAccountId(EntityId.of(tokenAccount.getAccountId(), ACCOUNT));
        tokenTransferId2.setTokenId(EntityId.of(tokenAccount.getTokenId(), TOKEN));
        tokenTransferId2.setConsensusTimestamp(tokenAccount.getCreatedTimestamp() + 2);
        var tokenTransfer2 = domainBuilder
                .tokenTransfer()
                .customize(t -> t.id(tokenTransferId2).amount(-50L))
                .get();
        expected.setBalance(tokenTransfer1.getAmount() + tokenTransfer2.getAmount());

        sqlEntityListener.onTokenTransfer(tokenTransfer2);
        if (commitIndex > 4) {
            completeFileAndCommit();
            assertThat(tokenAccountRepository.findAll()).containsExactly(expected);
            assertThat(findTokenAccountHistory()).containsExactly(tokenAccount);
            assertThat(tokenTransferRepository.findAll()).containsExactlyInAnyOrder(tokenTransfer1, tokenTransfer2);
        }

        var tokenTransferId3 = new TokenTransfer.Id();
        tokenTransferId3.setAccountId(EntityId.of(tokenAccount.getAccountId(), ACCOUNT));
        tokenTransferId3.setTokenId(EntityId.of(tokenAccount.getTokenId(), TOKEN));
        tokenTransferId3.setConsensusTimestamp(tokenAccount.getCreatedTimestamp() + 3);
        var tokenTransfer3 = domainBuilder
                .tokenTransfer()
                .customize(t -> t.id(tokenTransferId3).amount(20L))
                .get();

        sqlEntityListener.onTokenTransfer(tokenTransfer3);
        completeFileAndCommit();
        expected.setBalance(tokenTransfer1.getAmount() + tokenTransfer2.getAmount() + tokenTransfer3.getAmount());

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactly(expected);
        assertThat(findTokenAccountHistory()).containsExactly(tokenAccount);
        assertThat(tokenTransferRepository.findAll())
                .containsExactlyInAnyOrder(tokenTransfer1, tokenTransfer2, tokenTransfer3);
    }

    @ValueSource(ints = {1, 2})
    @ParameterizedTest
    void onSchedule(int commitIndex) {
        var schedule = domainBuilder.schedule().get();
        var expected = TestUtils.clone(schedule);

        sqlEntityListener.onSchedule(schedule);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(scheduleRepository.findAll()).containsOnly(expected);
        }

        var scheduleUpdate = new Schedule();
        scheduleUpdate.setExecutedTimestamp(domainBuilder.timestamp());
        scheduleUpdate.setScheduleId(schedule.getScheduleId());
        expected.setExecutedTimestamp(scheduleUpdate.getExecutedTimestamp());

        sqlEntityListener.onSchedule(scheduleUpdate);
        completeFileAndCommit();

        assertThat(scheduleRepository.findAll()).containsOnly(expected);
    }

    @Test
    void onScheduleExecutedWithoutScheduleCreate() {
        // For partial mirrornode which can miss a schedulecreate tx for an executed scheduled tx
        var schedule = new Schedule();
        schedule.setExecutedTimestamp(domainBuilder.timestamp());
        schedule.setScheduleId(domainBuilder.entityId(SCHEDULE).getId());
        sqlEntityListener.onSchedule(schedule);
        assertThat(scheduleRepository.findAll()).isEmpty();
    }

    @Test
    void onScheduleSignature() {
        TransactionSignature transactionSignature1 =
                domainBuilder.transactionSignature().get();
        TransactionSignature transactionSignature2 =
                domainBuilder.transactionSignature().get();
        TransactionSignature transactionSignature3 =
                domainBuilder.transactionSignature().get();

        // when
        sqlEntityListener.onTransactionSignature(transactionSignature1);
        sqlEntityListener.onTransactionSignature(transactionSignature2);
        sqlEntityListener.onTransactionSignature(transactionSignature3);
        completeFileAndCommit();

        // then
        assertThat(transactionSignatureRepository.findAll())
                .containsExactlyInAnyOrder(transactionSignature1, transactionSignature2, transactionSignature3);
    }

    @Test
    void onScheduleMerge() {
        Schedule schedule = domainBuilder.schedule().get();
        sqlEntityListener.onSchedule(schedule);

        Schedule scheduleUpdated = new Schedule();
        scheduleUpdated.setScheduleId(schedule.getScheduleId());
        scheduleUpdated.setExecutedTimestamp(5L);
        sqlEntityListener.onSchedule(scheduleUpdated);

        // when
        completeFileAndCommit();

        // then
        schedule.setExecutedTimestamp(5L);
        assertThat(scheduleRepository.findAll()).containsExactlyInAnyOrder(schedule);
    }

    @Test
    void onEthereumTransactionWInitCode() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(true).get();
        sqlEntityListener.onEthereumTransaction(ethereumTransaction);

        // when
        completeFileAndCommit();

        // then
        assertThat(ethereumTransactionRepository.findAll()).hasSize(1).first().isEqualTo(ethereumTransaction);
    }

    @Test
    void onEthereumTransactionWFileId() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).get();
        sqlEntityListener.onEthereumTransaction(ethereumTransaction);

        // when
        completeFileAndCommit();

        // then
        assertThat(ethereumTransactionRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(t -> assertThat(t.getCallDataId().getId())
                        .isEqualTo(ethereumTransaction.getCallDataId().getId()))
                .isEqualTo(ethereumTransaction);
    }

    private void completeFileAndCommit() {
        RecordFile recordFile = domainBuilder.recordFile().get();
        transactionTemplate.executeWithoutResult(status -> sqlEntityListener.onEnd(recordFile));

        assertThat(recordFileRepository.findAll()).contains(recordFile);
        assertThat(sidecarFileRepository.findAll()).containsAll(recordFile.getSidecars());
    }

    private Collection<TokenAccount> findTokenAccountHistory() {
        return findHistory(TokenAccount.class, "account_id, token_id");
    }

    private ContractState getContractState(ContractStateChange contractStateChange, long createdTimestamp) {
        var value = contractStateChange.getValueWritten() == null
                ? contractStateChange.getValueRead()
                : contractStateChange.getValueWritten();
        return ContractState.builder()
                .contractId(contractStateChange.getContractId())
                .createdTimestamp(createdTimestamp)
                .modifiedTimestamp(contractStateChange.getConsensusTimestamp())
                .slot(DomainUtils.leftPadBytes(contractStateChange.getSlot(), 32))
                .value(value)
                .build();
    }

    @SneakyThrows
    private Token getToken(EntityId tokenId, EntityId accountId, Long createdTimestamp, long modifiedTimestamp) {
        var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var hexKey = Key.newBuilder()
                .setEd25519(ByteString.copyFrom(Hex.decodeHex(instr)))
                .build();
        return getToken(
                tokenId,
                accountId,
                createdTimestamp,
                modifiedTimestamp,
                1000,
                false,
                hexKey,
                1_000_000_000L,
                hexKey,
                "FOO COIN TOKEN",
                hexKey,
                "FOOTOK",
                hexKey,
                hexKey,
                TokenPauseStatusEnum.UNPAUSED);
    }

    private Token getToken(
            EntityId tokenId,
            EntityId accountId,
            Long createdTimestamp,
            long modifiedTimestamp,
            Integer decimals,
            Boolean freezeDefault,
            Key freezeKey,
            Long initialSupply,
            Key kycKey,
            String name,
            Key supplyKey,
            String symbol,
            Key wipeKey,
            Key pauseKey,
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

    private Nft getNft(
            EntityId tokenId,
            long serialNumber,
            EntityId accountId,
            Long createdTimestamp,
            Boolean deleted,
            String metadata,
            long modifiedTimestamp) {
        Nft nft = new Nft();
        nft.setAccountId(accountId);
        nft.setCreatedTimestamp(createdTimestamp);
        nft.setDeleted(deleted);
        nft.setMetadata(metadata == null ? null : metadata.getBytes(StandardCharsets.UTF_8));
        nft.setId(new NftId(serialNumber, tokenId));
        nft.setModifiedTimestamp(modifiedTimestamp);

        return nft;
    }

    private TokenAccount getTokenAccount(
            EntityId tokenId,
            EntityId accountId,
            Long createdTimestamp,
            Boolean associated,
            Boolean autoAssociated,
            long balance,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            Range<Long> timestampRange) {
        TokenAccount tokenAccount = new TokenAccount();
        tokenAccount.setAccountId(accountId.getId());
        tokenAccount.setAssociated(associated);
        tokenAccount.setAutomaticAssociation(autoAssociated);
        tokenAccount.setBalance(balance);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        tokenAccount.setFreezeStatus(freezeStatus);
        tokenAccount.setKycStatus(kycStatus);
        tokenAccount.setTimestampRange(timestampRange);
        tokenAccount.setTokenId(tokenId.getId());
        return tokenAccount;
    }
}
