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

package com.hedera.mirror.web3.evm.store.accessor;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.services.utils.EntityIdUtils.idFromEntityId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.evm.exception.WrongTypeException;
import com.hedera.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import com.hedera.mirror.web3.repository.AccountBalanceRepository;
import com.hedera.mirror.web3.repository.CryptoAllowanceRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.inject.Named;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;

@Named
@RequiredArgsConstructor
public class AccountDatabaseAccessor extends DatabaseAccessor<Object, Account> {
    public static final long DEFAULT_AUTO_RENEW_PERIOD = 7776000L;

    private static final BinaryOperator<Long> NO_DUPLICATE_MERGE_FUNCTION = (v1, v2) -> {
        throw new IllegalStateException(String.format("Duplicate key for values %s and %s", v1, v2));
    };
    private final EntityDatabaseAccessor entityDatabaseAccessor;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NftRepository nftRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    @Override
    public @NonNull Optional<Account> get(@NonNull Object key, final Optional<Long> timestamp) {
        if (key instanceof Address address) {
            return entityDatabaseAccessor.get(address, timestamp).map(entity -> accountFromEntity(entity, timestamp));
        }
        throw new DatabaseAccessIncorrectKeyTypeException("Accessor for class %s failed to fetch by key of type %s"
                .formatted(Account.class.getTypeName(), key.getClass().getTypeName()));
    }

    private Account accountFromEntity(Entity entity, final Optional<Long> timestamp) {
        if (!ACCOUNT.equals(entity.getType()) && !CONTRACT.equals(entity.getType())) {
            throw new WrongTypeException("Trying to map an account/contract from a different type");
        }
        final var tokenAssociationsCounts =
                getNumberOfAllAndPositiveBalanceTokenAssociations(entity.getId(), timestamp);
        return new Account(
                entity.getEvmAddress() != null && entity.getEvmAddress().length > 0
                        ? ByteString.copyFrom(entity.getEvmAddress())
                        : ByteString.EMPTY,
                entity.getId(),
                new Id(entity.getShard(), entity.getRealm(), entity.getNum()),
                TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                getAccountBalance(entity, timestamp),
                Optional.ofNullable(entity.getDeleted()).orElse(false),
                getOwnedNfts(entity.getId(), timestamp),
                Optional.ofNullable(entity.getAutoRenewPeriod()).orElse(DEFAULT_AUTO_RENEW_PERIOD),
                idFromEntityId(entity.getProxyAccountId()),
                Optional.ofNullable(entity.getMaxAutomaticTokenAssociations()).orElse(0),
                getCryptoAllowances(entity.getId(), timestamp),
                getFungibleTokenAllowances(entity.getId(), timestamp),
                getApproveForAllNfts(entity.getId(), timestamp),
                tokenAssociationsCounts.all(),
                tokenAssociationsCounts.positive(),
                0,
                Optional.ofNullable(entity.getEthereumNonce()).orElse(0L),
                entity.getType().equals(CONTRACT),
                parseJkey(entity.getKey()),
                entity.getCreatedTimestamp() != null
                        ? TimeUnit.SECONDS.convert(entity.getCreatedTimestamp(), TimeUnit.NANOSECONDS)
                        : 0L);
    }

    private long getOwnedNfts(Long accountId, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> nftRepository.countByAccountIdAndTimestampNotDeleted(accountId, t))
                .orElseGet(() -> nftRepository.countByAccountIdNotDeleted(accountId));
    }

    private Long getAccountBalance(Entity entity, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(entity.getId(), t))
                .orElseGet(() -> Optional.ofNullable(entity.getBalance()))
                .orElse(0L);
    }

    private SortedMap<EntityNum, Long> getCryptoAllowances(Long ownerId, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> cryptoAllowanceRepository.findByOwnerAndTimestamp(ownerId, t))
                .orElseGet(() -> cryptoAllowanceRepository.findByOwner(ownerId))
                .stream()
                .collect(Collectors.toMap(
                        cryptoAllowance -> entityNumFromId(EntityId.of(cryptoAllowance.getSpender())),
                        CryptoAllowance::getAmount,
                        NO_DUPLICATE_MERGE_FUNCTION,
                        TreeMap::new));
    }

    private SortedMap<FcTokenAllowanceId, Long> getFungibleTokenAllowances(
            Long ownerId, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> tokenAllowanceRepository.findByOwnerAndTimestamp(ownerId, t))
                .orElseGet(() -> tokenAllowanceRepository.findByOwner(ownerId))
                .stream()
                .collect(Collectors.toMap(
                        tokenAllowance -> new FcTokenAllowanceId(
                                entityNumFromId(EntityId.of(tokenAllowance.getTokenId())),
                                entityNumFromId(EntityId.of(tokenAllowance.getSpender()))),
                        AbstractTokenAllowance::getAmount,
                        NO_DUPLICATE_MERGE_FUNCTION,
                        TreeMap::new));
    }

    private SortedSet<FcTokenAllowanceId> getApproveForAllNfts(Long ownerId, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> nftAllowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(ownerId, t))
                .orElseGet(() -> nftAllowanceRepository.findByOwnerAndApprovedForAllIsTrue(ownerId))
                .stream()
                .map(nftAllowance -> new FcTokenAllowanceId(
                        entityNumFromId(EntityId.of(nftAllowance.getTokenId())),
                        entityNumFromId(EntityId.of(nftAllowance.getSpender()))))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private EntityNum entityNumFromId(EntityId entityId) {
        return EntityNum.fromLong(entityId.getNum());
    }

    private TokenAccountBalances getNumberOfAllAndPositiveBalanceTokenAssociations(
            long accountId, final Optional<Long> timestamp) {
        var counts = timestamp
                .map(t -> tokenAccountRepository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, t))
                .orElseGet(() ->
                        tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(accountId));
        int all = 0;
        int positive = 0;

        for (TokenAccountAssociationsCount count : counts) {
            if (count.getIsPositiveBalance()) {
                positive = count.getTokenCount();
            }
            all += count.getTokenCount();
        }

        return new TokenAccountBalances(all, positive);
    }

    private JKey parseJkey(byte[] keyBytes) {
        try {
            return keyBytes == null ? null : asFcKeyUnchecked(Key.parseFrom(keyBytes));
        } catch (InvalidProtocolBufferException | IllegalArgumentException e) {
            return null;
        }
    }

    private record TokenAccountBalances(int all, int positive) {}
}
