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

import static com.hedera.mirror.common.domain.entity.EntityType.*;
import static com.hedera.services.utils.EntityIdUtils.idFromEntityId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.exception.WrongTypeException;
import com.hedera.mirror.web3.repository.*;
import com.hedera.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.Key;
import com.mysema.commons.lang.Pair;
import jakarta.inject.Named;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

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

    @Override
    public @NonNull Optional<Account> get(@NonNull Object address) {
        return entityDatabaseAccessor.get(address).map(this::accountFromEntity);
    }

    private Account accountFromEntity(Entity entity) {
        if (!ACCOUNT.equals(entity.getType()) && !CONTRACT.equals(entity.getType())) {
            throw new WrongTypeException("Trying to map an account/contract from a different type");
        }

        final var historicalRecordFile = ContractCallContext.get().getRecordFile();
        final var timestamp = (historicalRecordFile != null) ? historicalRecordFile.getConsensusEnd() : -1;
        final var tokenAssociationsCounts =
                getNumberOfAllAndPositiveBalanceTokenAssociations(entity.getId(), timestamp);
        return new Account(
                entity.getEvmAddress() != null && entity.getEvmAddress().length > 0
                        ? ByteString.copyFrom(entity.getEvmAddress())
                        : ByteString.EMPTY,
                entity.getId(),
                new Id(entity.getShard(), entity.getRealm(), entity.getNum()),
                TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                Optional.ofNullable(entity.getBalance()).orElse(0L),
                Optional.ofNullable(entity.getDeleted()).orElse(false),
                getOwnedNfts(entity.getId(), timestamp),
                Optional.ofNullable(entity.getAutoRenewPeriod()).orElse(DEFAULT_AUTO_RENEW_PERIOD),
                idFromEntityId(entity.getProxyAccountId()),
                Optional.ofNullable(entity.getMaxAutomaticTokenAssociations()).orElse(0),
                getCryptoAllowances(entity.getId(), timestamp),
                getFungibleTokenAllowances(entity.getId(), timestamp),
                getApproveForAllNfts(entity.getId(), timestamp),
                tokenAssociationsCounts.getFirst(),
                tokenAssociationsCounts.getSecond(),
                0,
                Optional.ofNullable(entity.getEthereumNonce()).orElse(0L),
                entity.getType().equals(CONTRACT),
                parseJkey(entity.getKey()),
                entity.getCreatedTimestamp() != null
                        ? TimeUnit.SECONDS.convert(entity.getCreatedTimestamp(), TimeUnit.NANOSECONDS)
                        : 0L);
    }

    private long getOwnedNfts(Long accountId, long timestamp) {
        return (timestamp != -1)
                ? nftRepository.countByAccountIdAndTimestampNotDeleted(accountId, timestamp)
                : nftRepository.countByAccountIdNotDeleted(accountId);
    }

    private SortedMap<EntityNum, Long> getCryptoAllowances(Long ownerId, long timestamp) {
        final var cryptoAllowances = (timestamp != -1)
                ? cryptoAllowanceRepository.findByOwnerAndTimestamp(ownerId, timestamp)
                : cryptoAllowanceRepository.findByOwner(ownerId);
        return cryptoAllowances.stream()
                .collect(Collectors.toMap(
                        cryptoAllowance -> entityNumFromId(EntityId.of(cryptoAllowance.getSpender())),
                        CryptoAllowance::getAmount,
                        NO_DUPLICATE_MERGE_FUNCTION,
                        TreeMap::new));
    }

    private SortedMap<FcTokenAllowanceId, Long> getFungibleTokenAllowances(Long ownerId, long timestamp) {
        final var tokenAllowances = (timestamp != -1)
                ? tokenAllowanceRepository.findByOwnerAndTimestamp(ownerId, timestamp)
                : tokenAllowanceRepository.findByOwner(ownerId);
        return tokenAllowances.stream()
                .collect(Collectors.toMap(
                        tokenAllowance -> new FcTokenAllowanceId(
                                entityNumFromId(EntityId.of(tokenAllowance.getTokenId())),
                                entityNumFromId(EntityId.of(tokenAllowance.getSpender()))),
                        AbstractTokenAllowance::getAmount,
                        NO_DUPLICATE_MERGE_FUNCTION,
                        TreeMap::new));
    }

    private SortedSet<FcTokenAllowanceId> getApproveForAllNfts(Long ownerId, long timestamp) {
        final var nftAllowances = (timestamp != -1)
                ? nftAllowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(ownerId, timestamp)
                : nftAllowanceRepository.findByOwnerAndApprovedForAllIsTrue(ownerId);
        return nftAllowances.stream()
                .map(nftAllowance -> new FcTokenAllowanceId(
                        entityNumFromId(EntityId.of(nftAllowance.getTokenId())),
                        entityNumFromId(EntityId.of(nftAllowance.getSpender()))))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private EntityNum entityNumFromId(EntityId entityId) {
        return EntityNum.fromLong(entityId.getNum());
    }

    private Pair<Integer, Integer> getNumberOfAllAndPositiveBalanceTokenAssociations(long accountId, long timestamp) {
        final var counts = (timestamp != -1)
                ? tokenAccountRepository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, timestamp)
                : tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(accountId);
        int all = 0;
        int positive = 0;

        for (TokenAccountAssociationsCount count : counts) {
            if (count.getIsPositiveBalance()) {
                positive = count.getTokenCount();
            }
            all += count.getTokenCount();
        }

        return new Pair<>(all, positive);
    }

    private JKey parseJkey(byte[] keyBytes) {
        try {
            return keyBytes == null ? null : asFcKeyUnchecked(Key.parseFrom(keyBytes));
        } catch (InvalidProtocolBufferException | IllegalArgumentException e) {
            return null;
        }
    }
}
