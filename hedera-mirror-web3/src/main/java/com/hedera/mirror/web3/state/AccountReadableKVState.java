/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.web3.state.Utils.DEFAULT_AUTO_RENEW_PERIOD;
import static com.hedera.mirror.web3.state.Utils.parseKey;
import static com.hedera.services.utils.EntityIdUtils.toAccountId;
import static com.hedera.services.utils.EntityIdUtils.toTokenId;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.AccountBalanceRepository;
import com.hedera.mirror.web3.repository.CryptoAllowanceRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import com.hedera.mirror.web3.utils.Suppliers;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in mirror-node
 *
 * The object, which is read from DB is converted to the PBJ generated format, so that it can properly be utilized by the hedera app components
 * */
@Named
public class AccountReadableKVState extends ReadableKVStateBase<AccountID, Account> {

    private final AccountBalanceRepository accountBalanceRepository;
    private final CommonEntityAccessor commonEntityAccessor;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NftRepository nftRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;

    public AccountReadableKVState(
            CommonEntityAccessor commonEntityAccessor,
            NftAllowanceRepository nftAllowanceRepository,
            NftRepository nftRepository,
            TokenAllowanceRepository tokenAllowanceRepository,
            CryptoAllowanceRepository cryptoAllowanceRepository,
            TokenAccountRepository tokenAccountRepository,
            AccountBalanceRepository accountBalanceRepository) {
        super("ACCOUNTS");
        this.accountBalanceRepository = accountBalanceRepository;
        this.commonEntityAccessor = commonEntityAccessor;
        this.cryptoAllowanceRepository = cryptoAllowanceRepository;
        this.nftAllowanceRepository = nftAllowanceRepository;
        this.nftRepository = nftRepository;
        this.tokenAccountRepository = tokenAccountRepository;
        this.tokenAllowanceRepository = tokenAllowanceRepository;
    }

    @Nullable
    @Override
    public Account get(@Nonnull AccountID key) {
        return readFromDataSource(key);
    }

    @Override
    protected Account readFromDataSource(@Nonnull AccountID key) {
        final var timestamp = ContractCallContext.getTimestamp();
        return commonEntityAccessor
                .get(key, timestamp)
                .filter(entity -> entity.getType() != TOKEN)
                .map(entity -> accountFromEntity(entity, timestamp))
                .orElse(null);
    }

    @Override
    protected @Nonnull Iterator<AccountID> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }

    private Account accountFromEntity(Entity entity, final Optional<Long> timestamp) {
        var tokenAccountBalances = getNumberOfAllAndPositiveBalanceTokenAssociations(entity.getId(), timestamp);
        byte[] alias = new byte[0];
        if (entity.getEvmAddress() != null && entity.getEvmAddress().length > 0) {
            alias = entity.getEvmAddress();
        } else if (entity.getAlias() != null && entity.getAlias().length > 0) {
            alias = entity.getAlias();
        }

        return Account.newBuilder()
                .accountId(EntityIdUtils.toAccountId(entity.toEntityId()))
                .alias(Bytes.wrap(alias))
                .approveForAllNftAllowances(getApproveForAllNfts(entity.getId(), timestamp))
                .autoRenewAccountId(toAccountId(entity.getAutoRenewAccountId()))
                .autoRenewSeconds(Objects.requireNonNullElse(entity.getAutoRenewPeriod(), DEFAULT_AUTO_RENEW_PERIOD))
                .cryptoAllowances(getCryptoAllowances(entity.getId(), timestamp))
                .deleted(Objects.requireNonNullElse(entity.getDeleted(), false))
                .ethereumNonce(Objects.requireNonNullElse(entity.getEthereumNonce(), 0L))
                .expirationSecond(TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS))
                .expiredAndPendingRemoval(false)
                .key(parseKey(entity.getKey()))
                .maxAutoAssociations(Objects.requireNonNullElse(entity.getMaxAutomaticTokenAssociations(), 0))
                .memo(entity.getMemo())
                .numberAssociations(() -> tokenAccountBalances.get().all())
                .numberOwnedNfts(getOwnedNfts(entity.getId(), timestamp))
                .numberPositiveBalances(() -> tokenAccountBalances.get().positive())
                .receiverSigRequired(entity.getReceiverSigRequired() != null && entity.getReceiverSigRequired())
                .smartContract(CONTRACT.equals(entity.getType()))
                .tinybarBalance(getAccountBalance(entity, timestamp))
                .tokenAllowances(getFungibleTokenAllowances(entity.getId(), timestamp))
                .build();
    }

    private Supplier<Long> getOwnedNfts(Long accountId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> nftRepository.countByAccountIdAndTimestampNotDeleted(accountId, t))
                .orElseGet(() -> nftRepository.countByAccountIdNotDeleted(accountId)));
    }

    /**
     * Determines account balance based on block context.
     *
     * Non-historical Call:
     * Get the balance from entity.getBalance()
     * Historical Call:
     * If the entity creation is after the passed timestamp - return 0L (the entity was not created)
     * Else get the balance from the historical query `findHistoricalAccountBalanceUpToTimestamp`
     */
    private Supplier<Long> getAccountBalance(final Entity entity, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> {
                    Long createdTimestamp = entity.getCreatedTimestamp();
                    if (createdTimestamp == null || t >= createdTimestamp) {
                        return accountBalanceRepository
                                .findHistoricalAccountBalanceUpToTimestamp(entity.getId(), t)
                                .orElse(0L);
                    } else {
                        return 0L;
                    }
                })
                .orElseGet(() -> Objects.requireNonNullElse(entity.getBalance(), 0L)));
    }

    private Supplier<List<AccountCryptoAllowance>> getCryptoAllowances(
            final Long ownerId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> cryptoAllowanceRepository.findByOwnerAndTimestamp(ownerId, t))
                .orElseGet(() -> cryptoAllowanceRepository.findByOwner(ownerId))
                .stream()
                .map(this::convertCryptoAllowance)
                .toList());
    }

    private Supplier<List<AccountFungibleTokenAllowance>> getFungibleTokenAllowances(
            final Long ownerId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> tokenAllowanceRepository.findByOwnerAndTimestamp(ownerId, t))
                .orElseGet(() -> tokenAllowanceRepository.findByOwner(ownerId))
                .stream()
                .map(this::convertFungibleAllowance)
                .toList());
    }

    private Supplier<List<AccountApprovalForAllAllowance>> getApproveForAllNfts(
            final Long ownerId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> nftAllowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(ownerId, t))
                .orElseGet(() -> nftAllowanceRepository.findByOwnerAndApprovedForAllIsTrue(ownerId))
                .stream()
                .map(this::convertNftAllowance)
                .toList());
    }

    private Supplier<TokenAccountBalances> getNumberOfAllAndPositiveBalanceTokenAssociations(
            long accountId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> getTokenAccountBalances(timestamp
                .map(t -> tokenAccountRepository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, t))
                .orElseGet(
                        () -> tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(accountId))
                .stream()
                .toList()));
    }

    private TokenAccountBalances getTokenAccountBalances(final List<TokenAccountAssociationsCount> counts) {
        int all = 0;
        int positive = 0;

        for (final var count : counts) {
            if (count.getIsPositiveBalance()) {
                positive = count.getTokenCount();
            }
            all += count.getTokenCount();
        }

        final var allAggregated = all;
        final var positiveAggregated = positive;

        return new TokenAccountBalances(allAggregated, positiveAggregated);
    }

    private AccountFungibleTokenAllowance convertFungibleAllowance(final TokenAllowance tokenAllowance) {
        return new AccountFungibleTokenAllowance(
                toTokenId(tokenAllowance.getTokenId()),
                toAccountId(tokenAllowance.getSpender()),
                tokenAllowance.getAmount());
    }

    private AccountCryptoAllowance convertCryptoAllowance(final CryptoAllowance cryptoAllowance) {
        return new AccountCryptoAllowance(toAccountId(cryptoAllowance.getSpender()), cryptoAllowance.getAmount());
    }

    private AccountApprovalForAllAllowance convertNftAllowance(final NftAllowance nftAllowance) {
        return new AccountApprovalForAllAllowance(
                toTokenId(nftAllowance.getTokenId()), toAccountId(nftAllowance.getSpender()));
    }

    private record TokenAccountBalances(int all, int positive) {}
}
