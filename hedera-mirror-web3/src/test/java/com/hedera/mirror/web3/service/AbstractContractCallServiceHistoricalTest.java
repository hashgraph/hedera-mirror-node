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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_PUBLIC_KEY;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.AbstractCustomFee;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.viewmodel.BlockType;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public abstract class AbstractContractCallServiceHistoricalTest extends AbstractContractCallServiceTest {

    protected Range<Long> setUpHistoricalContext(final long blockNumber) {
        final var recordFile = persistRecordFile(blockNumber);
        return setupHistoricalStateInService(blockNumber, recordFile);
    }

    protected RecordFile persistRecordFile(final long blockNumber) {
        return domainBuilder.recordFile().customize(f -> f.index(blockNumber)).persist();
    }

    protected Range<Long> setupHistoricalStateInService(final long blockNumber, final RecordFile recordFile) {
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        final var historicalRange = Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd());
        testWeb3jService.setHistoricalRange(historicalRange);
        return historicalRange;
    }

    protected void setupHistoricalStateInService(final long blockNumber, final Range<Long> timestampRange) {
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        testWeb3jService.setHistoricalRange(timestampRange);
    }

    protected Pair<Entity, Entity> persistAccountTokenRelationshipHistorical(
            final boolean isFrozen, final Range<Long> historicalRange) {
        final var account = persistAccountEntityHistoricalWithAlias(historicalRange);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        persistFungibleTokenHistorical(tokenEntity, historicalRange);
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .freezeStatus(isFrozen ? TokenFreezeStatusEnum.FROZEN : TokenFreezeStatusEnum.UNFROZEN)
                        .associated(true)
                        .timestampRange(historicalRange))
                .persist();
        return Pair.of(account, tokenEntity);
    }

    protected void persistTokenAccountFrozenRelationshipHistorical(
            final Entity tokenEntity, final Entity accountEntity, final Range<Long> historicalRange) {
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(accountEntity.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .associated(true)
                        .timestampRange(historicalRange))
                .persist();
    }

    protected Entity persistAccountEntityHistorical(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .deleted(false)
                        .balance(1_000_000_000_000L)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();
    }

    protected Entity persistAccountEntityNoEvmAddressHistorical(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .deleted(false)
                        .evmAddress(null)
                        .balance(1_000_000_000_000L)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();
    }

    protected Entity persistAccountEntityHistoricalWithAlias(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .alias(SENDER_PUBLIC_KEY.toByteArray())
                        .deleted(false)
                        .evmAddress(SENDER_ALIAS.toArray())
                        .balance(1_000_000_000_000L)
                        .createdTimestamp(timestampRange.lowerEndpoint())
                        .timestampRange(timestampRange))
                .persist();
    }

    protected Entity persistAccountWithBalanceHistorical(final long balance, final Range<Long> timestampRange) {
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.balance(balance)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();
        persistAccountBalanceHistorical(entity.toEntityId(), balance, timestampRange);

        return entity;
    }

    protected void persistAccountBalanceHistorical(
            final EntityId entityId, final long balance, final Range<Long> timestampRange) {
        // There needs to be an entry for account 0.0.2 in order for the account balance query to return the correct
        // result
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(timestampRange.lowerEndpoint(), EntityId.of(2)))
                        .balance(balance))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(timestampRange.lowerEndpoint(), entityId))
                        .balance(balance))
                .persist();
    }

    protected void persistTokenBalanceHistorical(
            final EntityId accountId, final EntityId tokenId, final long balance, final Range<Long> timestampRange) {
        // There needs to be an entry for account 0.0.2 in order for the token balance query to return the correct
        // result
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(timestampRange.lowerEndpoint(), EntityId.of(2)))
                        .balance(balance))
                .persist();
        domainBuilder
                .tokenBalance()
                .customize(ab -> ab.id(new TokenBalance.Id(timestampRange.lowerEndpoint(), accountId, tokenId))
                        .balance(balance))
                .persist();
    }

    protected Entity persistTokenEntityHistorical(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).timestampRange(timestampRange))
                .persist();
    }

    protected void persistFungibleTokenHistorical(Entity tokenEntity, final Range<Long> timestampRange) {
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();
    }

    protected Entity persistFungibleTokenHistorical(final Range<Long> timestampRange) {
        final var entity = persistTokenEntityHistorical(timestampRange);
        persistFungibleTokenHistorical(entity, timestampRange);
        return entity;
    }

    protected Entity persistNftHistorical(final Range<Long> timestampRange) {
        final var tokenEntity = persistTokenEntityHistorical(timestampRange);
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(timestampRange))
                .persist();
        domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).timestampRange(timestampRange))
                .persist();

        return tokenEntity;
    }

    protected void persistTokenAllowanceHistorical(
            final Entity tokenEntity,
            final Entity owner,
            final Entity spender,
            final long amount,
            final Range<Long> timestampRange) {
        domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.tokenId(tokenEntity.getId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .amount(amount)
                        .amountGranted(amount)
                        .timestampRange(timestampRange))
                .persist();
    }

    protected void persistNftAllowanceHistorical(
            final Entity tokenEntity, final Entity owner, final Entity spender, final Range<Long> timestampRange) {
        domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.tokenId(tokenEntity.getId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .timestampRange(timestampRange))
                .persist();
    }

    protected void persistCryptoAllowanceHistorical(
            final Entity owner, final EntityId spender, final long amount, final Range<Long> timestampRange) {
        domainBuilder
                .cryptoAllowanceHistory()
                .customize(ca -> ca.owner(owner.toEntityId().getId())
                        .spender(spender.getId())
                        .payerAccountId(owner.toEntityId())
                        .amount(amount)
                        .amountGranted(amount)
                        .timestampRange(timestampRange))
                .persist();
    }

    protected AbstractCustomFee persistCustomFeesWithFeeCollectorHistorical(
            final Entity feeCollector,
            final Entity tokenEntity,
            final TokenTypeEnum tokenType,
            final Range<Long> timestampRange) {
        final var fixedFee = com.hedera.mirror.common.domain.token.FixedFee.builder()
                .allCollectorsAreExempt(true)
                .amount(domainBuilder.number())
                .collectorAccountId(feeCollector.toEntityId())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();

        final var fractionalFee = TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)
                ? FractionalFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector.toEntityId())
                        .denominator(domainBuilder.number())
                        .maximumAmount(domainBuilder.number())
                        .minimumAmount(1L)
                        .numerator(domainBuilder.number())
                        .netOfTransfers(true)
                        .build()
                : null;

        final var fallbackFee = FallbackFee.builder()
                .amount(domainBuilder.number())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();

        final var royaltyFee = TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)
                ? RoyaltyFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector.toEntityId())
                        .denominator(domainBuilder.number())
                        .fallbackFee(fallbackFee)
                        .numerator(domainBuilder.number())
                        .build()
                : null;

        if (TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)) {
            return domainBuilder
                    .customFeeHistory()
                    .customize(f -> f.tokenId(tokenEntity.getId())
                            .fixedFees(List.of(fixedFee))
                            .fractionalFees(List.of(fractionalFee))
                            .royaltyFees(new ArrayList<>())
                            .timestampRange(timestampRange))
                    .persist();
        } else {
            return domainBuilder
                    .customFeeHistory()
                    .customize(f -> f.tokenId(tokenEntity.getId())
                            .fixedFees(List.of(fixedFee))
                            .royaltyFees(List.of(royaltyFee))
                            .fractionalFees(new ArrayList<>())
                            .timestampRange(timestampRange))
                    .persist();
        }
    }
}
