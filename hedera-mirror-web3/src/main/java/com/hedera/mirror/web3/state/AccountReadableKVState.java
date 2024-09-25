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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
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
import com.hedera.mirror.web3.utils.Suppliers;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.java.Log;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in mirror-node
 *
 * The object, which is read from DB is converted to the PBJ generated format, so that it can properly be utilized by the hedera app components
 * */
@Named
@Log
public class AccountReadableKVState extends ReadableKVStateBase<AccountID, Account> {
    private static final long DEFAULT_AUTO_RENEW_PERIOD = 7776000L;
    private static final String KEY = "ACCOUNTS";
    private static final Long ZERO_BALANCE = 0L;

    private final CommonEntityAccessor commonEntityAccessor;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NftRepository nftRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    public AccountReadableKVState(
            CommonEntityAccessor commonEntityAccessor,
            NftAllowanceRepository nftAllowanceRepository,
            NftRepository nftRepository,
            TokenAllowanceRepository tokenAllowanceRepository,
            CryptoAllowanceRepository cryptoAllowanceRepository,
            TokenAccountRepository tokenAccountRepository,
            AccountBalanceRepository accountBalanceRepository) {
        super(KEY);
        this.commonEntityAccessor = commonEntityAccessor;
        this.nftAllowanceRepository = nftAllowanceRepository;
        this.nftRepository = nftRepository;
        this.tokenAllowanceRepository = tokenAllowanceRepository;
        this.cryptoAllowanceRepository = cryptoAllowanceRepository;
        this.tokenAccountRepository = tokenAccountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
    }

    @Override
    protected Account readFromDataSource(@Nonnull AccountID key) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        return commonEntityAccessor
                .get(key, timestamp)
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

        return Account.newBuilder()
                .accountId(new AccountID(
                        entity.getShard(),
                        entity.getRealm(),
                        new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getNum())))
                .alias(
                        entity.getEvmAddress() != null && entity.getEvmAddress().length > 0
                                ? Bytes.wrap(entity.getEvmAddress())
                                : Bytes.EMPTY)
                .key(parseKey(entity))
                .expirationSecond(TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS))
                .tinybarBalance(getAccountBalance(entity, timestamp))
                .memo(entity.getMemo())
                .deleted(Optional.ofNullable(entity.getDeleted()).orElse(false))
                .receiverSigRequired(entity.getReceiverSigRequired() != null && entity.getReceiverSigRequired())
                .numberOwnedNfts(getOwnedNfts(entity.getId(), timestamp))
                .maxAutoAssociations(Optional.ofNullable(entity.getMaxAutomaticTokenAssociations())
                        .orElse(0))
                .numberAssociations(() -> tokenAccountBalances.get().all())
                .smartContract(CONTRACT.equals(entity.getType()))
                .numberPositiveBalances(() -> tokenAccountBalances.get().positive())
                .ethereumNonce(entity.getEthereumNonce() != null ? entity.getEthereumNonce() : 0L)
                .autoRenewAccountId(new AccountID(
                        entity.getShard(),
                        entity.getRealm(),
                        new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getAutoRenewAccountId())))
                .autoRenewSeconds(
                        entity.getAutoRenewPeriod() != null ? entity.getAutoRenewPeriod() : DEFAULT_AUTO_RENEW_PERIOD)
                .cryptoAllowances(getCryptoAllowances(entity.getId(), timestamp))
                .approveForAllNftAllowances(getApproveForAllNfts(entity.getId(), timestamp))
                .tokenAllowances(getFungibleTokenAllowances(entity.getId(), timestamp))
                .expiredAndPendingRemoval(false)
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
                                .orElse(ZERO_BALANCE);
                    } else {
                        return ZERO_BALANCE;
                    }
                })
                .orElseGet(() -> Optional.ofNullable(entity.getBalance()).orElse(ZERO_BALANCE)));
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
        var counts = timestamp
                .map(t -> tokenAccountRepository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, t))
                .orElseGet(() ->
                        tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(accountId));
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

        return Suppliers.memoize(() -> new TokenAccountBalances(allAggregated, positiveAggregated));
    }

    private AccountFungibleTokenAllowance convertFungibleAllowance(final TokenAllowance tokenAllowance) {
        return new AccountFungibleTokenAllowance(
                new TokenID(0L, 0L, tokenAllowance.getTokenId()),
                new com.hedera.hapi.node.base.AccountID(
                        0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, tokenAllowance.getSpender())),
                tokenAllowance.getAmount());
    }

    private AccountCryptoAllowance convertCryptoAllowance(final CryptoAllowance cryptoAllowance) {
        return new AccountCryptoAllowance(
                new com.hedera.hapi.node.base.AccountID(
                        0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, cryptoAllowance.getSpender())),
                cryptoAllowance.getAmount());
    }

    private AccountApprovalForAllAllowance convertNftAllowance(final NftAllowance nftAllowance) {
        return new AccountApprovalForAllAllowance(
                new TokenID(0L, 0L, nftAllowance.getTokenId()),
                new com.hedera.hapi.node.base.AccountID(
                        0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, nftAllowance.getSpender())));
    }

    private Key parseKey(Entity entity) {
        final byte[] keyBytes = entity.getKey();

        try {
            if (keyBytes != null && keyBytes.length > 0) {
                return Key.PROTOBUF.parse(Bytes.wrap(keyBytes));
            }
        } catch (final ParseException e) {
            log.warning("Failed to parse key for account " + entity.getId());
        }

        return Key.DEFAULT;
    }

    private record TokenAccountBalances(int all, int positive) {}
}
