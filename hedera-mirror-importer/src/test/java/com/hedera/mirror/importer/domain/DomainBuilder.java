package com.hedera.mirror.importer.domain;

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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.FILE;
import static com.hedera.mirror.common.domain.entity.EntityType.SCHEDULE;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignaturePair;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.NftTransferId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.common.util.DomainUtils;

@Log4j2
@Named
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DomainBuilder {

    public static final int KEY_LENGTH_ECDSA = 33;
    public static final int KEY_LENGTH_ED25519 = 32;

    private final AtomicLong id = new AtomicLong(0L);
    private final Instant now = Instant.now();
    private final SecureRandom random = new SecureRandom();
    private final Map<Class<?>, CrudRepository<?, ?>> repositories;

    // Intended for use by unit tests that don't need persistence
    public DomainBuilder() {
        this(Collections.emptyList());
    }

    @Autowired
    public DomainBuilder(Collection<CrudRepository<?, ?>> crudRepositories) {
        repositories = new HashMap<>();

        for (CrudRepository<?, ?> crudRepository : crudRepositories) {
            try {
                Class<?> domainClass = GenericTypeResolver.resolveTypeArguments(crudRepository.getClass(),
                        CrudRepository.class)[0];
                repositories.put(domainClass, crudRepository);
            } catch (Exception e) {
                log.warn("Unable to map repository {} to domain class", crudRepository.getClass());
            }
        }
    }

    public DomainPersister<Contract, Contract.ContractBuilder> contract() {
        long id = id();
        long timestamp = timestamp();

        Contract.ContractBuilder builder = Contract.builder()
                .autoRenewPeriod(1800L)
                .createdTimestamp(timestamp)
                .deleted(false)
                .expirationTimestamp(timestamp + 30_000_000L)
                .fileId(entityId(FILE))
                .id(id)
                .key(key())
                .memo(text(16))
                .obtainerId(entityId(CONTRACT))
                .proxyAccountId(entityId(ACCOUNT))
                .num(id)
                .realm(0L)
                .shard(0L)
                .timestampRange(Range.atLeast(timestamp))
                .type(CONTRACT);

        return new DomainPersister<>(getRepository(Contract.class), builder, builder::build);
    }

    public DomainPersister<ContractLog, ContractLog.ContractLogBuilder> contractLog() {
        ContractLog.ContractLogBuilder builder = ContractLog.builder()
                .bloom(bytes(256))
                .consensusTimestamp(timestamp())
                .contractId(entityId(CONTRACT))
                .data(bytes(128))
                .index((int) id())
                .payerAccountId(entityId(ACCOUNT))
                .topic0(bytes(64))
                .topic1(bytes(64))
                .topic2(bytes(64))
                .topic3(bytes(64));
        return new DomainPersister<>(getRepository(ContractLog.class), builder, builder::build);
    }

    public DomainPersister<ContractResult, ContractResult.ContractResultBuilder> contractResult() {
        ContractResult.ContractResultBuilder builder = ContractResult.builder()
                .amount(1000L)
                .bloom(bytes(256))
                .callResult(bytes(512))
                .consensusTimestamp(timestamp())
                .contractId(entityId(CONTRACT))
                .createdContractIds(List.of(entityId(CONTRACT).getId()))
                .errorMessage("")
                .functionParameters(bytes(64))
                .functionResult(bytes(128))
                .gasLimit(200L)
                .gasUsed(100L)
                .payerAccountId(entityId(ACCOUNT));
        return new DomainPersister<>(getRepository(ContractResult.class), builder, builder::build);
    }

    public DomainPersister<Entity, Entity.EntityBuilder> entity() {
        long id = id();
        long timestamp = timestamp();

        Entity.EntityBuilder builder = Entity.builder()
                .alias(key())
                .autoRenewAccountId(entityId(ACCOUNT))
                .autoRenewPeriod(1800L)
                .createdTimestamp(timestamp)
                .deleted(false)
                .expirationTimestamp(timestamp + 30_000_000L)
                .id(id)
                .key(key())
                .maxAutomaticTokenAssociations(0)
                .memo(text(16))
                .proxyAccountId(entityId(ACCOUNT))
                .num(id)
                .realm(0L)
                .receiverSigRequired(false)
                .shard(0L)
                .submitKey(key())
                .timestampRange(Range.atLeast(timestamp))
                .type(ACCOUNT);

        return new DomainPersister<>(getRepository(Entity.class), builder, builder::build);
    }

    public DomainPersister<NftTransfer, NftTransfer.NftTransferBuilder> nftTransfer() {
        NftTransfer.NftTransferBuilder builder = NftTransfer.builder()
                .id(new NftTransferId(timestamp(), 1L, entityId(TOKEN)))
                .receiverAccountId(entityId(ACCOUNT))
                .payerAccountId(entityId(ACCOUNT))
                .senderAccountId(entityId(ACCOUNT));

        return new DomainPersister<>(getRepository(NftTransfer.class), builder, builder::build);
    }

    public DomainPersister<NonFeeTransfer, NonFeeTransfer.NonFeeTransferBuilder> nonFeeTransfer() {
        NonFeeTransfer.NonFeeTransferBuilder builder = NonFeeTransfer.builder()
                .amount(100L)
                .id(new NonFeeTransfer.Id(timestamp(), entityId(ACCOUNT)))
                .payerAccountId(entityId(ACCOUNT));

        return new DomainPersister<>(getRepository(NonFeeTransfer.class), builder, builder::build);
    }

    public DomainPersister<RecordFile, RecordFile.RecordFileBuilder> recordFile() {
        long timestamp = timestamp();
        RecordFile.RecordFileBuilder builder = RecordFile.builder()
                .consensusStart(timestamp)
                .consensusEnd(timestamp + 1)
                .count(1L)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .fileHash(text(96))
                .hash(text(96))
                .index(id())
                .loadEnd(now.plusSeconds(1).getEpochSecond())
                .loadStart(now.getEpochSecond())
                .name(now.toString().replace(':', '_') + ".rcd")
                .nodeAccountId(entityId(ACCOUNT))
                .previousHash(text(96));
        return new DomainPersister<>(getRepository(RecordFile.class), builder, builder::build);
    }

    public DomainPersister<Schedule, Schedule.ScheduleBuilder> schedule() {
        Schedule.ScheduleBuilder builder = Schedule.builder()
                .consensusTimestamp(timestamp())
                .creatorAccountId(entityId(ACCOUNT))
                .payerAccountId(entityId(ACCOUNT))
                .scheduleId(entityId(SCHEDULE).getId())
                .transactionBody(bytes(64));
        return new DomainPersister<>(getRepository(Schedule.class), builder, builder::build);
    }

    public DomainPersister<Token, Token.TokenBuilder> token() {
        long timestamp = timestamp();
        Token.TokenBuilder builder = Token.builder()
                .createdTimestamp(timestamp)
                .decimals(1000)
                .feeScheduleKey(key())
                .freezeDefault(false)
                .freezeKey(key())
                .initialSupply(1_000_000_000L)
                .kycKey(key())
                .modifiedTimestamp(timestamp)
                .name("Hbars")
                .pauseKey(key())
                .pauseStatus(TokenPauseStatusEnum.UNPAUSED)
                .supplyKey(key())
                .symbol("HBAR")
                .tokenId(new TokenId(entityId(TOKEN)))
                .treasuryAccountId(entityId(ACCOUNT))
                .wipeKey(key());
        return new DomainPersister<>(getRepository(Token.class), builder, builder::build);
    }

    public DomainPersister<TokenTransfer, TokenTransfer.TokenTransferBuilder> tokenTransfer() {
        TokenTransfer.TokenTransferBuilder builder = TokenTransfer.builder()
                .amount(100L)
                .id(new TokenTransfer.Id(timestamp(), entityId(TOKEN), entityId(ACCOUNT)))
                .payerAccountId(entityId(ACCOUNT))
                .tokenDissociate(false);

        return new DomainPersister<>(getRepository(TokenTransfer.class), builder, builder::build);
    }

    public DomainPersister<TransactionSignature, TransactionSignature.TransactionSignatureBuilder> transactionSignature() {
        TransactionSignature.TransactionSignatureBuilder builder = TransactionSignature.builder()
                .consensusTimestamp(timestamp())
                .entityId(entityId(ACCOUNT))
                .publicKeyPrefix(bytes(16))
                .signature(bytes(32))
                .type(SignaturePair.SignatureCase.ED25519.getNumber());
        return new DomainPersister<>(getRepository(TransactionSignature.class), builder, builder::build);
    }

    // Helper methods
    public byte[] bytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    public EntityId entityId(EntityType type) {
        return EntityId.of(0L, 0L, id(), type);
    }

    public long id() {
        return id.incrementAndGet();
    }

    public byte[] key() {
        if (id.get() % 2 == 0) {
            ByteString bytes = ByteString.copyFrom(bytes(KEY_LENGTH_ECDSA));
            return Key.newBuilder().setECDSASecp256K1(bytes).build().toByteArray();
        } else {
            ByteString bytes = ByteString.copyFrom(bytes(KEY_LENGTH_ED25519));
            return Key.newBuilder().setEd25519(bytes).build().toByteArray();
        }
    }

    public String text(int characters) {
        return RandomStringUtils.randomAlphanumeric(characters);
    }

    public long timestamp() {
        return DomainUtils.convertToNanosMax(now.getEpochSecond(), now.getNano()) + id();
    }

    private <T> CrudRepository<T, ?> getRepository(Class<T> domainClass) {
        return (CrudRepository<T, ?>) repositories.get(domainClass);
    }
}
