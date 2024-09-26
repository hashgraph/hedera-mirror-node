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

import static com.hedera.mirror.web3.state.Utils.DEFAULT_AUTO_RENEW_PERIOD;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.CustomFee.FeeOneOfType;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.CustomFeeRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.mirror.web3.utils.Suppliers;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.util.CollectionUtils;

public class TokenReadableKVState extends ReadableKVStateBase<TokenID, Token> {
    private static final String KEY = "TOKENS";

    private final CommonEntityAccessor commonEntityAccessor;
    private final CustomFeeRepository customFeeRepository;
    private final TokenRepository tokenRepository;
    private final EntityRepository entityRepository;

    protected TokenReadableKVState(
            @Nonnull CommonEntityAccessor commonEntityAccessor,
            @Nonnull CustomFeeRepository customFeeRepository,
            @Nonnull TokenRepository tokenRepository,
            @Nonnull EntityRepository entityRepository) {
        super(KEY);
        this.commonEntityAccessor = commonEntityAccessor;
        this.customFeeRepository = customFeeRepository;
        this.tokenRepository = tokenRepository;
        this.entityRepository = entityRepository;
    }

    @Override
    protected Token readFromDataSource(@Nonnull TokenID key) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var entity = commonEntityAccessor.get(key, timestamp).orElse(null);

        if (entity == null) {
            return null;
        }

        final var token = timestamp
                .flatMap(t -> tokenRepository.findByTokenIdAndTimestamp(entity.getId(), t))
                .orElseGet(() -> tokenRepository.findById(entity.getId()).orElse(null));

        if (token == null) {
            return null;
        }

        return tokenFromEntities(entity, token, timestamp);
    }

    @Override
    protected @Nonnull Iterator<TokenID> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }

    private Token tokenFromEntities(
            final Entity entity,
            final com.hedera.mirror.common.domain.token.Token token,
            final Optional<Long> timestamp) {
        return Token.newBuilder()
                .tokenId(new TokenID(entity.getShard(), entity.getRealm(), entity.getNum()))
                .name(token.getName())
                .symbol(token.getSymbol())
                .decimals(token.getDecimals())
                .totalSupply(getTotalSupply(token, timestamp))
                .treasuryAccountId(getTreasury(token.getTreasuryAccountId(), timestamp))
                .adminKey(Utils.parseKey(entity.getKey()))
                .kycKey(Utils.parseKey(token.getKycKey()))
                .freezeKey(Utils.parseKey(token.getFreezeKey()))
                .wipeKey(Utils.parseKey(token.getWipeKey()))
                .supplyKey(Utils.parseKey(token.getSupplyKey()))
                .feeScheduleKey(Utils.parseKey(token.getFeeScheduleKey()))
                .pauseKey(Utils.parseKey(token.getPauseKey()))
                .deleted(false)
                .tokenType(TokenType.valueOf(token.getType().name()))
                .supplyType(TokenSupplyType.valueOf(token.getSupplyType().name()))
                .autoRenewAccountId(getAutoRenewAccount(entity.getAutoRenewAccountId(), timestamp))
                .autoRenewSeconds(
                        entity.getAutoRenewPeriod() != null ? entity.getAutoRenewPeriod() : DEFAULT_AUTO_RENEW_PERIOD)
                .expirationSecond(TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS))
                .memo(entity.getMemo())
                .maxSupply(token.getMaxSupply())
                .paused(token.getPauseStatus().equals(TokenPauseStatusEnum.PAUSED))
                .accountsFrozenByDefault(token.getFreezeDefault())
                .metadata(Bytes.wrap(token.getMetadata()))
                .metadataKey(Utils.parseKey(token.getMetadataKey()))
                .customFees(getCustomFees(token.getTokenId(), timestamp))
                .build();
    }

    private Supplier<Long> getTotalSupply(
            final com.hedera.mirror.common.domain.token.Token token, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> getTotalSupplyHistorical(token.getTokenId(), t))
                .or(() -> Optional.ofNullable(token.getTotalSupply()))
                .orElse(0L));
    }

    private Long getTotalSupplyHistorical(long tokenId, long timestamp) {
        return tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(tokenId, timestamp);
    }

    private Supplier<AccountID> getAutoRenewAccount(Long autoRenewAccountId, final Optional<Long> timestamp) {
        if (autoRenewAccountId == null) {
            return null;
        }
        return Suppliers.memoize(() -> timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(autoRenewAccountId, t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(autoRenewAccountId))
                .map(Utils::convertCanonicalAccountIdFromEntity)
                .orElse(null));
    }

    private Supplier<AccountID> getTreasury(EntityId treasuryId, final Optional<Long> timestamp) {
        if (treasuryId == null) {
            return null;
        }
        return Suppliers.memoize(() -> timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(treasuryId.getId(), t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(treasuryId.getId()))
                .map(Utils::convertCanonicalAccountIdFromEntity)
                .orElse(null));
    }

    private Supplier<List<CustomFee>> getCustomFees(Long tokenId, final Optional<Long> timestamp) {
        final var customFees = timestamp
                .map(t -> customFeeRepository.findByTokenIdAndTimestamp(tokenId, t))
                .orElseGet(() -> customFeeRepository.findById(tokenId))
                .map(customFee -> convertCustomFees(customFee, timestamp));

        return Suppliers.memoize(() -> Collections.unmodifiableList(customFees.orElse(new ArrayList<>())));
    }

    private List<CustomFee> convertCustomFees(
            final com.hedera.mirror.common.domain.token.CustomFee customFees, final Optional<Long> timestamp) {
        var customFeesConstructed = new ArrayList<CustomFee>();
        customFeesConstructed.addAll(mapFixedFees(customFees, timestamp));
        customFeesConstructed.addAll(mapFractionalFees(customFees, timestamp));
        customFeesConstructed.addAll(mapRoyaltyFees(customFees, timestamp));
        return customFeesConstructed;
    }

    private List<CustomFee> mapFixedFees(
            final com.hedera.mirror.common.domain.token.CustomFee customFee, final Optional<Long> timestamp) {
        if (CollectionUtils.isEmpty(customFee.getFixedFees())) {
            return Collections.emptyList();
        }

        var fixedFees = new ArrayList<CustomFee>();
        customFee.getFixedFees().forEach(f -> {
            final var collector =
                    commonEntityAccessor.getAccountWithCanonicalAddress(f.getCollectorAccountId(), timestamp);
            final var denominatingTokenId = f.getDenominatingTokenId();

            final var fixedFee = new FixedFee(
                    f.getAmount(),
                    new TokenID(
                            denominatingTokenId.getShard(),
                            denominatingTokenId.getRealm(),
                            denominatingTokenId.getNum()));

            var constructed = new CustomFee(
                    new OneOf<>(FeeOneOfType.FIXED_FEE, fixedFee), collector, f.isAllCollectorsAreExempt());
            fixedFees.add(constructed);
        });

        return fixedFees;
    }

    private List<CustomFee> mapFractionalFees(
            final com.hedera.mirror.common.domain.token.CustomFee customFee, final Optional<Long> timestamp) {
        if (CollectionUtils.isEmpty(customFee.getFractionalFees())) {
            return Collections.emptyList();
        }

        var fractionalFees = new ArrayList<CustomFee>();
        customFee.getFractionalFees().forEach(f -> {
            final var collector =
                    commonEntityAccessor.getAccountWithCanonicalAddress(f.getCollectorAccountId(), timestamp);
            final var fractionalFee = new FractionalFee(
                    new Fraction(f.getNumerator(), f.getDenominator()),
                    f.getMinimumAmount(),
                    f.getMaximumAmount(),
                    f.isNetOfTransfers());
            var constructed = new CustomFee(
                    new OneOf<>(FeeOneOfType.FRACTIONAL_FEE, fractionalFee), collector, f.isAllCollectorsAreExempt());
            fractionalFees.add(constructed);
        });

        return fractionalFees;
    }

    private List<CustomFee> mapRoyaltyFees(
            com.hedera.mirror.common.domain.token.CustomFee customFee, final Optional<Long> timestamp) {
        if (CollectionUtils.isEmpty(customFee.getRoyaltyFees())) {
            return Collections.emptyList();
        }

        var royaltyFees = new ArrayList<CustomFee>();
        customFee.getRoyaltyFees().forEach(f -> {
            final var collector =
                    commonEntityAccessor.getAccountWithCanonicalAddress(f.getCollectorAccountId(), timestamp);
            final var fallbackFee = f.getFallbackFee();

            FixedFee convertedFallbackFee = null;
            if (fallbackFee != null) {
                final var denominatingTokenId = fallbackFee.getDenominatingTokenId();
                convertedFallbackFee = new FixedFee(
                        fallbackFee.getAmount(),
                        new TokenID(
                                denominatingTokenId.getShard(),
                                denominatingTokenId.getRealm(),
                                denominatingTokenId.getNum()));
            }
            final var royaltyFee =
                    new RoyaltyFee(new Fraction(f.getNumerator(), f.getDenominator()), convertedFallbackFee);
            var constructed = new CustomFee(
                    new OneOf<>(FeeOneOfType.ROYALTY_FEE, royaltyFee), collector, f.isAllCollectorsAreExempt());
            royaltyFees.add(constructed);
        });

        return royaltyFees;
    }
}
