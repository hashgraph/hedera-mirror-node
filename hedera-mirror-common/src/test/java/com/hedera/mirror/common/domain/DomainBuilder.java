package com.hedera.mirror.common.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignaturePair;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

import com.hedera.mirror.common.aggregator.LogsBloomAggregator;
import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
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
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.common.domain.transaction.TransactionType;
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
    private final AtomicInteger transactionIndex = new AtomicInteger(0);
    private final Instant now = Instant.now();
    private final SecureRandom random = new SecureRandom();

    // Intended for use by unit tests that don't need persistence
    public DomainBuilder() {
        this(null, null);
    }

    public DomainWrapper<AccountBalance, AccountBalance.AccountBalanceBuilder> accountBalance() {
        var builder = AccountBalance.builder()
                .balance(10L)
                .id(new AccountBalance.Id(timestamp(), entityId(ACCOUNT)));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AccountBalanceFile, AccountBalanceFile.AccountBalanceFileBuilder> accountBalanceFile() {
        long timestamp = timestamp();
        var name = Instant.ofEpochSecond(0L, timestamp).toString().replace(':', '_') + "_Balances.pb.gz";
        var builder = AccountBalanceFile.builder()
                .bytes(bytes(16))
                .consensusTimestamp(timestamp)
                .count(1L)
                .fileHash(text(96))
                .loadEnd(timestamp + 1)
                .loadStart(timestamp)
                .name(name)
                .nodeAccountId(entityId(ACCOUNT))
                .timeOffset(0);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AddressBook, AddressBook.AddressBookBuilder> addressBook() {
        var builder = AddressBook.builder()
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
        var builder = AddressBookEntry.builder()
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

        var builder = AddressBookServiceEndpoint.builder()
                .consensusTimestamp(timestamp())
                .ipAddressV4(ipAddress)
                .nodeId(id())
                .port(50211);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Contract, Contract.ContractBuilder> contract() {
        long id = id();
        long timestamp = timestamp();

        var builder = Contract.builder()
                .autoRenewAccountId(id())
                .autoRenewPeriod(1800L)
                .createdTimestamp(timestamp)
                .deleted(false)
                .evmAddress(evmAddress())
                .expirationTimestamp(timestamp + 30_000_000L)
                .fileId(entityId(FILE))
                .id(id)
                .key(key())
                .maxAutomaticTokenAssociations(2)
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
        var builder = ContractLog.builder()
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
        var builder = ContractResult.builder()
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
                .payerAccountId(entityId(ACCOUNT))
                .senderId(entityId(ACCOUNT));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractStateChange, ContractStateChange.ContractStateChangeBuilder> contractStateChange() {
        var builder = ContractStateChange.builder()
                .consensusTimestamp(timestamp())
                .contractId(entityId(CONTRACT).getId())
                .payerAccountId(entityId(ACCOUNT))
                .slot(bytes(128))
                .valueRead(bytes(64))
                .valueWritten(bytes(64));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CryptoAllowance, CryptoAllowance.CryptoAllowanceBuilder> cryptoAllowance() {
        var builder = CryptoAllowance.builder()
                .amount(10)
                .owner(entityId(ACCOUNT).getId())
                .payerAccountId(entityId(ACCOUNT))
                .spender(entityId(ACCOUNT).getId())
                .timestampRange(Range.atLeast(timestamp()));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CryptoTransfer, CryptoTransfer.CryptoTransferBuilder> cryptoTransfer() {
        var builder = CryptoTransfer.builder()
                .amount(10L)
                .consensusTimestamp(timestamp())
                .entityId(entityId(ACCOUNT).getId())
                .isApproval(false)
                .payerAccountId(entityId(ACCOUNT));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Entity, Entity.EntityBuilder> entity() {
        long id = id();
        long timestamp = timestamp();

        var builder = Entity.builder()
                .alias(key())
                .autoRenewAccountId(id())
                .autoRenewPeriod(1800L)
                .createdTimestamp(timestamp)
                .deleted(false)
                .ethereumNonce(1L)
                .evmAddress(evmAddress())
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

    public DomainWrapper<EthereumTransaction, EthereumTransaction.EthereumTransactionBuilder> ethereumTransaction(
            boolean hasInitCode) {
        var builder = EthereumTransaction.builder()
                .accessList(bytes(100))
                .chainId(bytes(1))
                .consensusTimestamp(timestamp())
                .data(bytes(100))
                .gasLimit(Long.MAX_VALUE)
                .gasPrice(bytes(32))
                .hash(bytes(32))
                .maxGasAllowance(Long.MAX_VALUE)
                .maxFeePerGas(bytes(32))
                .maxPriorityFeePerGas(bytes(32))
                .nonce(1234L)
                .payerAccountId(entityId(ACCOUNT))
                .recoveryId(3)
                .signatureR(bytes(32))
                .signatureS(bytes(32))
                .signatureV(bytes(1))
                .toAddress(bytes(20))
                .type(2)
                .value(bytes(32));

        if (hasInitCode) {
            builder.callData(bytes(100));
        } else {
            builder.callDataId(entityId(FILE));
        }

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Nft, Nft.NftBuilder> nft() {
        var createdTimestamp = timestamp();
        var builder = Nft.builder()
                .accountId(entityId(ACCOUNT))
                .createdTimestamp(createdTimestamp)
                .deleted(false)
                .id(new NftId(id(), entityId(TOKEN)))
                .metadata(bytes(16))
                .modifiedTimestamp(createdTimestamp);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftAllowance, NftAllowance.NftAllowanceBuilder> nftAllowance() {
        var builder = NftAllowance.builder()
                .approvedForAll(false)
                .owner(entityId(ACCOUNT).getId())
                .payerAccountId(entityId(ACCOUNT))
                .spender(entityId(ACCOUNT).getId())
                .timestampRange(Range.atLeast(timestamp()))
                .tokenId(entityId(TOKEN).getId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftTransfer, NftTransfer.NftTransferBuilder> nftTransfer() {
        var builder = NftTransfer.builder()
                .id(new NftTransferId(timestamp(), 1L, entityId(TOKEN)))
                .receiverAccountId(entityId(ACCOUNT))
                .payerAccountId(entityId(ACCOUNT))
                .senderAccountId(entityId(ACCOUNT));

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NonFeeTransfer, NonFeeTransfer.NonFeeTransferBuilder> nonFeeTransfer() {
        var builder = NonFeeTransfer.builder()
                .amount(100L)
                .consensusTimestamp(timestamp())
                .entityId(entityId(ACCOUNT))
                .payerAccountId(entityId(ACCOUNT));

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<RecordFile, RecordFile.RecordFileBuilder> recordFile() {
        // reset transaction index
        transactionIndex.set(0);

        long timestamp = timestamp();
        var builder = RecordFile.builder()
                .consensusStart(timestamp)
                .consensusEnd(timestamp + 1)
                .count(1L)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .fileHash(text(96))
                .gasUsed(100L)
                .hash(text(96))
                .index(id())
                .logsBloom(bytes(LogsBloomAggregator.BYTE_SIZE))
                .loadEnd(now.plusSeconds(1).getEpochSecond())
                .loadStart(now.getEpochSecond())
                .name(now.toString().replace(':', '_') + ".rcd")
                .nodeAccountId(entityId(ACCOUNT))
                .previousHash(text(96));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Schedule, Schedule.ScheduleBuilder> schedule() {
        var builder = Schedule.builder()
                .consensusTimestamp(timestamp())
                .creatorAccountId(entityId(ACCOUNT))
                .expirationTime(timestamp())
                .payerAccountId(entityId(ACCOUNT))
                .scheduleId(entityId(SCHEDULE).getId())
                .transactionBody(bytes(64))
                .waitForExpiry(true);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Token, Token.TokenBuilder> token() {
        long timestamp = timestamp();
        var builder = Token.builder()
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

    public DomainWrapper<TokenAllowance, TokenAllowance.TokenAllowanceBuilder> tokenAllowance() {
        var builder = TokenAllowance.builder()
                .amount(10L)
                .owner(entityId(ACCOUNT).getId())
                .payerAccountId(entityId(ACCOUNT))
                .spender(entityId(ACCOUNT).getId())
                .timestampRange(Range.atLeast(timestamp()))
                .tokenId(entityId(TOKEN).getId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenBalance, TokenBalance.TokenBalanceBuilder> tokenBalance() {
        var builder = TokenBalance.builder()
                .balance(1L)
                .id(new TokenBalance.Id(timestamp(), entityId(ACCOUNT), entityId(TOKEN)));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenTransfer, TokenTransfer.TokenTransferBuilder> tokenTransfer() {
        var builder = TokenTransfer.builder()
                .amount(100L)
                .id(new TokenTransfer.Id(timestamp(), entityId(TOKEN), entityId(ACCOUNT)))
                .payerAccountId(entityId(ACCOUNT))
                .tokenDissociate(false);

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Entity, Entity.EntityBuilder> topic() {
        return entity().customize(e -> e.alias(null)
                .receiverSigRequired(null)
                .ethereumNonce(null)
                .evmAddress(null)
                .maxAutomaticTokenAssociations(null)
                .proxyAccountId(null)
                .type(TOPIC));
    }

    public DomainWrapper<Transaction, Transaction.TransactionBuilder> transaction() {
        var builder = Transaction.builder()
                .chargedTxFee(10000000L)
                .consensusTimestamp(timestamp())
                .entityId(entityId(ACCOUNT))
                .index(transactionIndex())
                .initialBalance(10000000L)
                .maxFee(100000000L)
                .memo(bytes(10))
                .nodeAccountId(entityId(ACCOUNT))
                .nonce(0)
                .parentConsensusTimestamp(timestamp())
                .payerAccountId(entityId(ACCOUNT))
                .result(ResponseCodeEnum.SUCCESS.getNumber())
                .scheduled(false)
                .transactionBytes(bytes(100))
                .transactionHash(bytes(48))
                .type(TransactionType.CRYPTOTRANSFER.getProtoId())
                .validStartNs(timestamp())
                .validDurationSeconds(120L);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TransactionSignature, TransactionSignature.TransactionSignatureBuilder> transactionSignature() {
        var builder = TransactionSignature.builder()
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

    public byte[] evmAddress() {
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

    private int transactionIndex() {
        return transactionIndex.getAndIncrement();
    }

    @Value
    private class DomainWrapperImpl<T, B> implements DomainWrapper<T, B> {

        private final B builder;
        private final Supplier<T> supplier;

        @Override
        public DomainWrapper<T, B> customize(Consumer<B> customizer) {
            customizer.accept(builder);
            return this;
        }

        @Override
        public T get() {
            return supplier.get();
        }

        // The DomainBuilder can be used without an active ApplicationContext. If so, this method shouldn't be used.
        @Override
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
