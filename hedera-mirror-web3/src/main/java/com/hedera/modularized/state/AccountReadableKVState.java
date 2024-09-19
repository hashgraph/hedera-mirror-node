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

package com.hedera.modularized.state;

import static com.hedera.hapi.node.state.token.codec.AccountProtoCodec.STAKED_ID_UNSET;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
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
import com.hedera.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import com.hedera.mirror.web3.utils.Suppliers;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

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

    /**
     * Create a new AccountReadableKVState.
     */
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
    protected Account readFromDataSource(@NotNull AccountID key) {
        final var timestampValue = ContractCallContext.get().getTimestamp();
        final Optional<Long> timestamp = timestampValue != 0 ? Optional.of(timestampValue) : Optional.empty();
        return commonEntityAccessor
                .get(key, timestamp)
                .map(entity -> accountFromEntity(entity, timestamp))
                .orElse(null);
    }

    @Override
    protected @NotNull Iterator<AccountID> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }

    private com.hedera.hapi.node.state.token.Account accountFromEntity(Entity entity, final Optional<Long> timestamp) {
        var tokenAccountBalances = getNumberOfAllAndPositiveBalanceTokenAssociations(entity.getId(), timestamp);

        return new com.hedera.hapi.node.state.token.Account(
                new com.hedera.hapi.node.base.AccountID(
                        entity.getShard(),
                        entity.getRealm(),
                        new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getNum())),
                entity.getEvmAddress() != null && entity.getEvmAddress().length > 0
                        ? Bytes.wrap(entity.getEvmAddress())
                        : Bytes.EMPTY,
                parseKey(entity),
                TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                getAccountBalance(entity, timestamp),
                entity.getMemo(),
                Optional.ofNullable(entity.getDeleted()).orElse(false),
                0L,
                0L,
                STAKED_ID_UNSET,
                true,
                entity.getReceiverSigRequired() != null ? entity.getReceiverSigRequired() : false,
                TokenID.DEFAULT,
                NftID.DEFAULT,
                0L,
                getOwnedNfts(entity.getId(), timestamp),
                Optional.ofNullable(entity.getMaxAutomaticTokenAssociations()).orElse(0),
                0,
                tokenAccountBalances.get().all(),
                CONTRACT.equals(entity.getType()),
                tokenAccountBalances.get().positive(),
                entity.getEthereumNonce() != null ? entity.getEthereumNonce() : 0L,
                0L,
                new com.hedera.hapi.node.base.AccountID(
                        entity.getShard(),
                        entity.getRealm(),
                        new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getAutoRenewAccountId())),
                entity.getAutoRenewPeriod() != null ? entity.getAutoRenewPeriod() : DEFAULT_AUTO_RENEW_PERIOD,
                0,
                getCryptoAllowances(entity.getId(), timestamp),
                getApproveForAllNfts(entity.getId(), timestamp),
                getFungibleTokenAllowances(entity.getId(), timestamp),
                0,
                false,
                Bytes.EMPTY,
                PendingAirdropId.DEFAULT);
    }

    private Long getOwnedNfts(Long accountId, final Optional<Long> timestamp) {
        return timestamp
                .map(aLong -> nftRepository.countByAccountIdAndTimestampNotDeleted(accountId, aLong))
                .orElseGet(() -> nftRepository.countByAccountIdNotDeleted(accountId));
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
    private Long getAccountBalance(Entity entity, final Optional<Long> timestamp) {
        if (timestamp.isPresent()) {
            Long createdTimestamp = entity.getCreatedTimestamp();
            if (createdTimestamp == null || timestamp.get() >= createdTimestamp) {
                return accountBalanceRepository
                        .findHistoricalAccountBalanceUpToTimestamp(entity.getId(), timestamp.get())
                        .orElse(ZERO_BALANCE);
            } else {
                return ZERO_BALANCE;
            }
        }

        return entity.getBalance() != null ? entity.getBalance() : ZERO_BALANCE;
    }

    private List<AccountCryptoAllowance> getCryptoAllowances(Long ownerId, final Optional<Long> timestamp) {
        final var cryptoAllowances = timestamp.isPresent()
                ? cryptoAllowanceRepository.findByOwnerAndTimestamp(ownerId, timestamp.get())
                : cryptoAllowanceRepository.findByOwner(ownerId);

        return cryptoAllowances.stream().map(this::convertCryptoAllowance).collect(Collectors.toList());
    }

    private AccountCryptoAllowance convertCryptoAllowance(final CryptoAllowance cryptoAllowance) {
        return new AccountCryptoAllowance(
                new com.hedera.hapi.node.base.AccountID(
                        0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, cryptoAllowance.getSpender())),
                cryptoAllowance.getAmount());
    }

    private List<AccountFungibleTokenAllowance> getFungibleTokenAllowances(
            Long ownerId, final Optional<Long> timestamp) {
        final var fungibleAllowances = timestamp.isPresent()
                ? tokenAllowanceRepository.findByOwnerAndTimestamp(ownerId, timestamp.get())
                : tokenAllowanceRepository.findByOwner(ownerId);

        return fungibleAllowances.stream().map(this::convertFungibleAllowance).collect(Collectors.toList());
    }

    private AccountFungibleTokenAllowance convertFungibleAllowance(final TokenAllowance tokenAllowance) {
        return new AccountFungibleTokenAllowance(
                new TokenID(0L, 0L, tokenAllowance.getTokenId()),
                new com.hedera.hapi.node.base.AccountID(
                        0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, tokenAllowance.getSpender())),
                tokenAllowance.getAmount());
    }

    private List<AccountApprovalForAllAllowance> getApproveForAllNfts(Long ownerId, final Optional<Long> timestamp) {
        final var nftAllowances = timestamp.isPresent()
                ? nftAllowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(ownerId, timestamp.get())
                : nftAllowanceRepository.findByOwnerAndApprovedForAllIsTrue(ownerId);

        return nftAllowances.stream().map(this::convertNftAllowance).collect(Collectors.toList());
    }

    private AccountApprovalForAllAllowance convertNftAllowance(final NftAllowance nftAllowance) {
        return new AccountApprovalForAllAllowance(
                new TokenID(0L, 0L, nftAllowance.getTokenId()),
                new com.hedera.hapi.node.base.AccountID(
                        0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, nftAllowance.getSpender())));
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

        for (TokenAccountAssociationsCount count : counts) {
            if (count.getIsPositiveBalance()) {
                positive = count.getTokenCount();
            }
            all += count.getTokenCount();
        }

        final var allAggregated = all;
        final var positiveAggregated = positive;

        return Suppliers.memoize(() -> new TokenAccountBalances(allAggregated, positiveAggregated));
    }

    private com.hedera.hapi.node.base.Key parseKey(Entity entity) {
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
