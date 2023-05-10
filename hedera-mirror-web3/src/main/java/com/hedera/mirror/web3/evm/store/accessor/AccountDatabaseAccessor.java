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

import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.repository.CryptoAllowanceRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import java.sql.Date;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.beans.factory.annotation.Autowired;

@Named
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AccountDatabaseAccessor extends DatabaseAccessor<Address, Account> {
    public static final long DEFAULT_EXPIRY_TIMESTAMP = Date.valueOf("2100-1-1").getTime() * 1000;
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
    public @NonNull Optional<Account> get(@NonNull Address address) {
        return entityDatabaseAccessor.get(address).map(this::accountFromEntity);
    }

    private Account accountFromEntity(Entity entity) {
        return new Account(
                new Id(entity.getShard(), entity.getRealm(), entity.getNum()),
                getExpiration(entity),
                Optional.ofNullable(entity.getBalance()).orElse(0L),
                Optional.ofNullable(entity.getDeleted()).orElse(false),
                getOwnedNfts(entity.toEntityId()),
                Optional.ofNullable(entity.getAutoRenewPeriod()).orElse(Long.MAX_VALUE),
                idFromEntityId(entity.getProxyAccountId()),
                entity.getMaxAutomaticTokenAssociations(),
                getCryptoAllowances(entity.getId()),
                getFungibleTokenAllowances(entity.getId()),
                getApproveForAllNfts(entity.getId()),
                getNumTokenAssociations(entity.getId()),
                getPositiveBalances(entity.getId()),
                0);
    }

    private Long getExpiration(Entity entity) {
        if (entity.getExpirationTimestamp() != null) {
            return entity.getExpirationTimestamp();
        }

        if (entity.getCreatedTimestamp() != null && entity.getAutoRenewPeriod() != null) {
            return entity.getCreatedTimestamp() + entity.getAutoRenewPeriod();
        }

        return DEFAULT_EXPIRY_TIMESTAMP;
    }

    private long getOwnedNfts(EntityId accountId) {
        return nftRepository.countByAccountId(accountId);
    }

    private Id idFromEntityId(EntityId entityId) {
        if (entityId == null) {
            return null;
        }
        return new Id(entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum());
    }

    private SortedMap<EntityNum, Long> getCryptoAllowances(Long ownerId) {
        return cryptoAllowanceRepository.findByOwner(ownerId).stream()
                .collect(Collectors.toMap(
                        cryptoAllowance -> EntityNum.fromLong(cryptoAllowance.getSpender()),
                        CryptoAllowance::getAmount,
                        NO_DUPLICATE_MERGE_FUNCTION,
                        TreeMap::new));
    }

    private SortedMap<FcTokenAllowanceId, Long> getFungibleTokenAllowances(Long ownerId) {
        return tokenAllowanceRepository.findByOwner(ownerId).stream()
                .collect(Collectors.toMap(
                        tokenAllowance -> new FcTokenAllowanceId(
                                EntityNum.fromLong(tokenAllowance.getTokenId()),
                                EntityNum.fromLong(tokenAllowance.getSpender())),
                        AbstractTokenAllowance::getAmount,
                        NO_DUPLICATE_MERGE_FUNCTION,
                        TreeMap::new));
    }

    private SortedSet<FcTokenAllowanceId> getApproveForAllNfts(Long ownerId) {
        return nftAllowanceRepository.findByOwnerAndApprovedForAllIsTrue(ownerId).stream()
                .map(nftAllowance -> new FcTokenAllowanceId(
                        EntityNum.fromLong(nftAllowance.getTokenId()), EntityNum.fromLong(nftAllowance.getSpender())))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private int getNumTokenAssociations(long accountId) {
        return tokenAccountRepository.countByAccountIdAndAssociatedIsTrue(accountId);
    }

    private int getPositiveBalances(long accountId) {
        return tokenAccountRepository.countByAccountIdAndPositiveBalance(accountId);
    }
}
