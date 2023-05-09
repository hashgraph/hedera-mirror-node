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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;

import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.repository.CryptoAllowanceRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AccountDatabaseAccessor extends DatabaseAccessor<Address, Account> {
    public static final BinaryOperator<Long> NO_DUPLICATE_MERGE_FUNCTION = (v1, v2) -> {
        throw new IllegalStateException(String.format("Duplicate key for values %s and %s", v1, v2));
    };
    private final EntityRepository entityRepository;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NftRepository nftRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;

    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final TokenAccountRepository tokenAccountRepository;

    @Override
    public @NonNull Optional<Account> get(@NonNull Address address) {
        return getEntity(address).map(this::accountFromEntity);
    }

    private Optional<Entity> getEntity(Address address) {
        var addressBytes = address.toArrayUnsafe();
        if (isMirror(addressBytes)) {
            final var entityId = entityIdNumFromEvmAddress(address);
            return entityRepository.findByIdAndDeletedIsFalse(entityId);
        } else {
            return entityRepository.findByEvmAddressAndDeletedIsFalse(addressBytes);
        }
    }

    private Account accountFromEntity(Entity entity) {
        EntityId entityId = entity.toEntityId();
        return new Account(
                idFromEntityId(entityId),
                entity.getExpirationTimestamp(),
                entity.getBalance(),
                entity.getDeleted(),
                getOwnedNfts(entityId),
                entity.getAutoRenewPeriod(),
                idFromEntityId(entity.getProxyAccountId()),
                entity.getMaxAutomaticTokenAssociations(),
                getCryptoAllowances(entity.getId()),
                getFungibleTokenAllowances(entity.getId()),
                getApproveForAllNfts(entity.getId()),
                getNumTokenAssociations(entity.getId()),
                getPositiveBalances(entity.getId()),
                0);
    }

    private Id idFromEntityId(EntityId entityId) {
        return new Id(entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum());
    }

    private long getOwnedNfts(EntityId accountId) {
        return nftRepository.countByAccountId(accountId);
    }

    private SortedMap<EntityNum, Long> getCryptoAllowances(Long spenderId) {
        return cryptoAllowanceRepository.findBySpender(spenderId).stream()
                .collect(Collectors.toMap(
                        cryptoAllowance -> EntityNum.fromLong(cryptoAllowance.getOwner()),
                        CryptoAllowance::getAmount,
                        NO_DUPLICATE_MERGE_FUNCTION,
                        TreeMap::new));
    }

    private SortedMap<FcTokenAllowanceId, Long> getFungibleTokenAllowances(Long spenderId) {
        return tokenAllowanceRepository.findBySpender(spenderId).stream()
                .collect(Collectors.toMap(
                        tokenAllowance -> new FcTokenAllowanceId(
                                EntityNum.fromLong(tokenAllowance.getTokenId()),
                                EntityNum.fromLong(tokenAllowance.getSpender())),
                        AbstractTokenAllowance::getAmount,
                        NO_DUPLICATE_MERGE_FUNCTION,
                        TreeMap::new));
    }

    private SortedSet<FcTokenAllowanceId> getApproveForAllNfts(Long spenderId) {
        return nftAllowanceRepository.findBySpenderAndApprovedForAllIsTrue(spenderId).stream()
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
