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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.web3.repository.CryptoAllowanceRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDatabaseAccessorTest {
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address ADDRESS = Address.fromHexString(HEX);

    private Entity entity;

    @InjectMocks
    private AccountDatabaseAccessor accountAccessor;

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private NftAllowanceRepository nftAllowanceRepository;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private TokenAllowanceRepository tokenAllowanceRepository;

    @Mock
    private CryptoAllowanceRepository cryptoAllowanceRepository;

    @Mock
    private TokenAccountRepository tokenAccountRepository;

    private static final long SHARD = 0L;

    private static final long REALM = 1L;
    private static final long EXPIRATION_TIMESTAMP = 2L;
    private static final long BALANCE = 3L;
    private static final long AUTO_RENEW_PERIOD = 4L;
    private static final EntityId PROXY_ACCOUNT_ID = new EntityId(SHARD, REALM, 5L, EntityType.ACCOUNT);

    private static final int MAX_AUTOMATIC_TOKEN_ASSOCIATIONS = 6;

    private static final int POSITIVE_BALANCES = 7;
    private static final int NEGATIVE_BALANCES = 8;

    private static final List<TokenAccountAssociationsCount> associationsCount = Arrays.asList(
            new TokenAccountAssociationsCount() {
                @Override
                public Integer getTokenCount() {
                    return POSITIVE_BALANCES;
                }

                @Override
                public boolean getIsPositiveBalance() {
                    return true;
                }
            },
            new TokenAccountAssociationsCount() {
                @Override
                public Integer getTokenCount() {
                    return NEGATIVE_BALANCES;
                }

                @Override
                public boolean getIsPositiveBalance() {
                    return false;
                }
            });

    @BeforeEach
    void setup() {
        final var entityNum = entityIdNumFromEvmAddress(ADDRESS);
        entity = new Entity();
        entity.setId(entityNum);
        entity.setShard(SHARD);
        entity.setRealm(REALM);
        entity.setNum(entityNum);
        entity.setExpirationTimestamp(EXPIRATION_TIMESTAMP);
        entity.setBalance(BALANCE);
        entity.setDeleted(false);
        entity.setAutoRenewPeriod(AUTO_RENEW_PERIOD);
        entity.setProxyAccountId(PROXY_ACCOUNT_ID);
        entity.setMaxAutomaticTokenAssociations(MAX_AUTOMATIC_TOKEN_ASSOCIATIONS);
        when(entityDatabaseAccessor.get(any())).thenReturn(Optional.ofNullable(entity));
    }

    @Test
    void accountFieldsMatchEntityFields() {
        assertThat(accountAccessor.get(ADDRESS)).hasValueSatisfying(account -> assertThat(account)
                .returns(new Id(entity.getShard(), entity.getRealm(), entity.getNum()), Account::getId)
                .returns(entity.getExpirationTimestamp(), Account::getExpiry)
                .returns(entity.getBalance(), Account::getBalance)
                .returns(entity.getAutoRenewPeriod(), Account::getAutoRenewSecs)
                .returns(
                        new Id(
                                entity.getProxyAccountId().getShardNum(),
                                entity.getProxyAccountId().getRealmNum(),
                                entity.getProxyAccountId().getEntityNum()),
                        Account::getProxy)
                .returns(entity.getMaxAutomaticTokenAssociations(), Account::getMaxAutomaticAssociations));
    }

    @Test
    void whenExpirationTimestampIsNullThenExpiryIsBasedOnCreatedAndRenewTimestamps() {
        entity.setExpirationTimestamp(null);
        entity.setCreatedTimestamp(987L);
        long expectedExpiry = entity.getCreatedTimestamp() + TimeUnit.SECONDS.toNanos(entity.getAutoRenewPeriod());

        assertThat(accountAccessor.get(ADDRESS))
                .hasValueSatisfying(account -> assertThat(account).returns(expectedExpiry, Account::getExpiry));
    }

    @Test
    void useDefaultValuesWhenFieldsAreNull() {
        entity.setExpirationTimestamp(null);
        entity.setCreatedTimestamp(null);
        entity.setAutoRenewPeriod(null);
        entity.setMaxAutomaticTokenAssociations(null);
        entity.setBalance(null);
        entity.setDeleted(null);
        entity.setProxyAccountId(null);

        assertThat(accountAccessor.get(ADDRESS)).hasValueSatisfying(account -> assertThat(account)
                .returns(AccountDatabaseAccessor.DEFAULT_EXPIRY_TIMESTAMP, Account::getExpiry)
                .returns(0L, Account::getBalance)
                .returns(false, Account::isDeleted)
                .returns(AccountDatabaseAccessor.DEFAULT_AUTO_RENEW_PERIOD, Account::getAutoRenewSecs)
                .returns(0, Account::getMaxAutomaticAssociations)
                .returns(null, Account::getProxy));
    }

    @Test
    void accountOwnedNftsMatchesValueFromRepository() {
        long ownedNfts = 20;
        when(nftRepository.countByAccountIdNotDeleted(any())).thenReturn(ownedNfts);

        assertThat(accountAccessor.get(ADDRESS))
                .hasValueSatisfying(account -> assertThat(account).returns(ownedNfts, Account::getOwnedNfts));
    }

    @Test
    void cryptoAllowancesMatchValuesFromRepository() {
        CryptoAllowance firstAllowance = new CryptoAllowance();
        firstAllowance.setSpender(123L);
        firstAllowance.setOwner(entity.getId());
        firstAllowance.setAmount(50L);

        CryptoAllowance secondAllowance = new CryptoAllowance();
        secondAllowance.setSpender(234L);
        secondAllowance.setOwner(entity.getId());
        secondAllowance.setAmount(60L);

        when(cryptoAllowanceRepository.findByOwner(anyLong()))
                .thenReturn(Arrays.asList(firstAllowance, secondAllowance));

        SortedMap<EntityNum, Long> allowancesMap = new TreeMap<>();
        allowancesMap.put(EntityNum.fromLong(firstAllowance.getSpender()), firstAllowance.getAmount());
        allowancesMap.put(EntityNum.fromLong(secondAllowance.getSpender()), secondAllowance.getAmount());

        assertThat(accountAccessor.get(ADDRESS)).hasValueSatisfying(account -> assertThat(account)
                .returns(allowancesMap, Account::getCryptoAllowances));
    }

    @Test
    void fungibleTokenAllowancesMatchValuesFromRepository() {
        TokenAllowance firstAllowance = new TokenAllowance();
        firstAllowance.setOwner(entity.getId());
        firstAllowance.setTokenId(15L);
        firstAllowance.setSpender(123L);
        firstAllowance.setAmount(50L);

        TokenAllowance secondAllowance = new TokenAllowance();
        secondAllowance.setOwner(entity.getId());
        secondAllowance.setTokenId(16L);
        secondAllowance.setSpender(234L);
        secondAllowance.setAmount(60L);

        when(tokenAllowanceRepository.findByOwner(entity.getId()))
                .thenReturn(Arrays.asList(firstAllowance, secondAllowance));

        SortedMap<FcTokenAllowanceId, Long> allowancesMap = new TreeMap<>();
        allowancesMap.put(
                new FcTokenAllowanceId(
                        EntityNum.fromLong(firstAllowance.getTokenId()),
                        EntityNum.fromLong(firstAllowance.getSpender())),
                firstAllowance.getAmount());
        allowancesMap.put(
                new FcTokenAllowanceId(
                        EntityNum.fromLong(secondAllowance.getTokenId()),
                        EntityNum.fromLong(secondAllowance.getSpender())),
                secondAllowance.getAmount());

        assertThat(accountAccessor.get(ADDRESS)).hasValueSatisfying(account -> assertThat(account)
                .returns(allowancesMap, Account::getFungibleTokenAllowances));
    }

    @Test
    void approveForAllNftsMatchValuesFromRepository() {
        NftAllowance firstAllowance = new NftAllowance();
        firstAllowance.setOwner(entity.getId());
        firstAllowance.setTokenId(15L);
        firstAllowance.setSpender(123L);

        NftAllowance secondAllowance = new NftAllowance();
        secondAllowance.setOwner(entity.getId());
        secondAllowance.setTokenId(16L);
        secondAllowance.setSpender(234L);

        when(nftAllowanceRepository.findByOwnerAndApprovedForAllIsTrue(entity.getId()))
                .thenReturn(Arrays.asList(firstAllowance, secondAllowance));

        SortedSet<FcTokenAllowanceId> allowancesSet = new TreeSet<>();
        allowancesSet.add(new FcTokenAllowanceId(
                EntityNum.fromLong(firstAllowance.getTokenId()), EntityNum.fromLong(firstAllowance.getSpender())));
        allowancesSet.add(new FcTokenAllowanceId(
                EntityNum.fromLong(secondAllowance.getTokenId()), EntityNum.fromLong(secondAllowance.getSpender())));

        assertThat(accountAccessor.get(ADDRESS)).hasValueSatisfying(account -> assertThat(account)
                .returns(allowancesSet, Account::getApproveForAllNfts));
    }

    @Test
    void numTokenAssociationsAndNumPositiveBalancesMatchValuesFromRepository() {
        when(tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(anyLong()))
                .thenReturn(associationsCount);

        assertThat(accountAccessor.get(ADDRESS)).hasValueSatisfying(account -> assertThat(account)
                .returns(POSITIVE_BALANCES + NEGATIVE_BALANCES, Account::getNumAssociations)
                .returns(POSITIVE_BALANCES, Account::getNumPositiveBalances));
    }
}
