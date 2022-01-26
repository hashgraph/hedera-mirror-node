package com.hedera.mirror.common.domain;

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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
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

@Component
@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DomainBuilder {

    public static final int KEY_LENGTH_ECDSA = 33;
    public static final int KEY_LENGTH_ED25519 = 32;

    private final EntityManager entityManager;
    private final TransactionOperations transactionOperations;
    private final AtomicLong id = new AtomicLong(0L);
    private final Instant now = Instant.now();
    private final SecureRandom random = new SecureRandom();

    // Intended for use by unit tests that don't need persistence
    public DomainBuilder() {
        this(null, null);
    }

    public DomainWrapper<AddressBook, AddressBook.AddressBookBuilder> addressBook() {
        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .fileData(bytes(10))
                .fileId(EntityId.of(0L, 0L, 102, FILE))
                .nodeCount(6)
                .startConsensusTimestamp(timestamp())
                .endConsensusTimestamp(timestamp());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AddressBookEntry, AddressBookEntry.AddressBookEntryBuilder> addressBookEntry() {
        return addressBookEntry(0);
    }

    public DomainWrapper<AddressBookEntry, AddressBookEntry.AddressBookEntryBuilder> addressBookEntry(int endpoints) {
        long consensusTimestamp = timestamp();
        long nodeId = id();
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .description(text(10))
                .memo(text(10))
                .nodeId(nodeId)
                .nodeAccountId(EntityId.of(0L, 0L, nodeId + 3, ACCOUNT))
                .nodeCertHash(bytes(96))
                .publicKey(text(64))
                .stake(0L);

        var serviceEndpoints = new HashSet<AddressBookServiceEndpoint>();
        builder.serviceEndpoints(serviceEndpoints);

        for (int i = 0; i < endpoints; ++i) {
            var endpoint = addressBookServiceEndpoint()
                    .customize(a -> a.consensusTimestamp(consensusTimestamp).nodeId(nodeId))
                    .get();
            serviceEndpoints.add(endpoint);
        }

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AddressBookServiceEndpoint, AddressBookServiceEndpoint.AddressBookServiceEndpointBuilder> addressBookServiceEndpoint() {
        String ipAddress = "";
        try {
            ipAddress = InetAddress.getByAddress(bytes(4)).getHostAddress();
        } catch (UnknownHostException e) {
            // This shouldn't happen
        }

        AddressBookServiceEndpoint.AddressBookServiceEndpointBuilder builder = AddressBookServiceEndpoint.builder()
                .consensusTimestamp(timestamp())
                .ipAddressV4(ipAddress)
                .nodeId(id())
                .port(50211);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Contract, Contract.ContractBuilder> contract() {
        long id = id();
        long timestamp = timestamp();

        Contract.ContractBuilder builder = Contract.builder()
                .autoRenewPeriod(1800L)
                .createdTimestamp(timestamp)
                .deleted(false)
                .evmAddress(create2EvmAddress())
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

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractLog, ContractLog.ContractLogBuilder> contractLog() {
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
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractResult, ContractResult.ContractResultBuilder> contractResult() {
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
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Entity, Entity.EntityBuilder> entity() {
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

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftTransfer, NftTransfer.NftTransferBuilder> nftTransfer() {
        NftTransfer.NftTransferBuilder builder = NftTransfer.builder()
                .id(new NftTransferId(timestamp(), 1L, entityId(TOKEN)))
                .receiverAccountId(entityId(ACCOUNT))
                .payerAccountId(entityId(ACCOUNT))
                .senderAccountId(entityId(ACCOUNT));

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NonFeeTransfer, NonFeeTransfer.NonFeeTransferBuilder> nonFeeTransfer() {
        NonFeeTransfer.NonFeeTransferBuilder builder = NonFeeTransfer.builder()
                .amount(100L)
                .id(new NonFeeTransfer.Id(timestamp(), entityId(ACCOUNT)))
                .payerAccountId(entityId(ACCOUNT));

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<RecordFile, RecordFile.RecordFileBuilder> recordFile() {
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
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Schedule, Schedule.ScheduleBuilder> schedule() {
        Schedule.ScheduleBuilder builder = Schedule.builder()
                .consensusTimestamp(timestamp())
                .creatorAccountId(entityId(ACCOUNT))
                .payerAccountId(entityId(ACCOUNT))
                .scheduleId(entityId(SCHEDULE).getId())
                .transactionBody(bytes(64));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Token, Token.TokenBuilder> token() {
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
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenTransfer, TokenTransfer.TokenTransferBuilder> tokenTransfer() {
        TokenTransfer.TokenTransferBuilder builder = TokenTransfer.builder()
                .amount(100L)
                .id(new TokenTransfer.Id(timestamp(), entityId(TOKEN), entityId(ACCOUNT)))
                .payerAccountId(entityId(ACCOUNT))
                .tokenDissociate(false);

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TransactionSignature, TransactionSignature.TransactionSignatureBuilder> transactionSignature() {
        TransactionSignature.TransactionSignatureBuilder builder = TransactionSignature.builder()
                .consensusTimestamp(timestamp())
                .entityId(entityId(ACCOUNT))
                .publicKeyPrefix(bytes(16))
                .signature(bytes(32))
                .type(SignaturePair.SignatureCase.ED25519.getNumber());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    // Helper methods
    public byte[] bytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    public byte[] create2EvmAddress() {
        return bytes(20);
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

    @Value
    private class DomainWrapperImpl<T, B> implements DomainWrapper<T, B> {

        private final B builder;
        private final Supplier<T> supplier;

        public DomainWrapper<T, B> customize(Consumer<B> customizer) {
            customizer.accept(builder);
            return this;
        }

        public T get() {
            return supplier.get();
        }

        // The DomainBuilder can be used without an active ApplicationContext. If so, this method shouldn't be used.
        public T persist() {
            T entity = get();

            if (entityManager == null) {
                throw new IllegalStateException("Unable to persist entity without an EntityManager");
            }

            transactionOperations.executeWithoutResult(t -> entityManager.persist(entity));
            log.trace("Inserted {}", entity);
            return entity;
        }
    }
}
