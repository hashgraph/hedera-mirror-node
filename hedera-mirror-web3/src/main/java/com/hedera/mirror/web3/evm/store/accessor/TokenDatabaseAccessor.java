/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.evm.exception.WrongTypeException;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class TokenDatabaseAccessor extends DatabaseAccessor<Object, Token> {

    private final TokenRepository tokenRepository;
    private final EntityDatabaseAccessor entityDatabaseAccessor;
    private final EntityRepository entityRepository;
    private final CustomFeeDatabaseAccessor customFeeDatabaseAccessor;
    private final NftRepository nftRepository;

    @Override
    public @NonNull Optional<Token> get(@NonNull Object key, final Optional<Long> timestamp) {
        return entityDatabaseAccessor.get(key, timestamp).map(entity -> tokenFromEntity(entity, timestamp));
    }

    private Token tokenFromEntity(Entity entity, final Optional<Long> timestamp) {
        if (!TOKEN.equals(entity.getType())) {
            throw new WrongTypeException("Trying to map token from a different type");
        }
        final var databaseToken = timestamp
                .flatMap(t -> tokenRepository.findByTokenIdAndTimestamp(entity.getId(), t))
                .orElseGet(() -> tokenRepository.findById(entity.getId()).orElse(null));

        if (databaseToken == null) {
            return null;
        }
        return new Token(
                entity.getId(),
                new Id(entity.getShard(), entity.getRealm(), entity.getNum()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                false,
                Optional.ofNullable(databaseToken.getType())
                        .map(t -> TokenType.valueOf(t.name()))
                        .orElse(null),
                Optional.ofNullable(databaseToken.getSupplyType())
                        .map(st -> TokenSupplyType.valueOf(st.name()))
                        .orElse(null),
                getTotalSupply(databaseToken, timestamp),
                databaseToken.getMaxSupply(),
                parseJkey(databaseToken.getKycKey()),
                parseJkey(databaseToken.getFreezeKey()),
                parseJkey(databaseToken.getSupplyKey()),
                parseJkey(databaseToken.getWipeKey()),
                parseJkey(entity.getKey()),
                parseJkey(databaseToken.getFeeScheduleKey()),
                parseJkey(databaseToken.getPauseKey()),
                Boolean.TRUE.equals(databaseToken.getFreezeDefault()),
                getTreasury(databaseToken.getTreasuryAccountId(), timestamp),
                getAutoRenewAccount(entity.getAutoRenewAccountId(), timestamp),
                Optional.ofNullable(entity.getDeleted()).orElse(false),
                TokenPauseStatusEnum.PAUSED.equals(databaseToken.getPauseStatus()),
                false,
                TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                entity.getCreatedTimestamp() != null
                        ? TimeUnit.SECONDS.convert(entity.getCreatedTimestamp(), TimeUnit.NANOSECONDS)
                        : 0L,
                false,
                entity.getMemo(),
                databaseToken.getName(),
                databaseToken.getSymbol(),
                Optional.ofNullable(databaseToken.getDecimals()).orElse(0),
                Optional.ofNullable(entity.getAutoRenewPeriod()).orElse(0L),
                0L,
                getCustomFees(entity.getId(), timestamp));
    }

    private Long getTotalSupply(
            final com.hedera.mirror.common.domain.token.Token token, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> Optional.of(getTotalSupplyHistorical(
                        token.getType().equals(TokenTypeEnum.FUNGIBLE_COMMON), token.getTokenId(), t)))
                .orElseGet(() -> Optional.ofNullable(token.getTotalSupply()))
                .orElse(0L);
    }

    private Long getTotalSupplyHistorical(boolean isFungible, long tokenId, long timestamp) {
        if (isFungible) {
            return tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(tokenId, timestamp);
        } else {
            return nftRepository.findNftTotalSupplyByTokenIdAndTimestamp(tokenId, timestamp);
        }
    }

    private JKey parseJkey(byte[] keyBytes) {
        try {
            return keyBytes == null ? null : asFcKeyUnchecked(Key.parseFrom(keyBytes));
        } catch (InvalidProtocolBufferException | IllegalArgumentException e) {
            return null;
        }
    }

    private Account getAutoRenewAccount(Long autoRenewAccountId, final Optional<Long> timestamp) {
        if (autoRenewAccountId == null) {
            return null;
        }
        return timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(autoRenewAccountId, t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(autoRenewAccountId))
                .map(autoRenewAccount -> new Account(
                        autoRenewAccount.getId(),
                        new Id(autoRenewAccount.getShard(), autoRenewAccount.getRealm(), autoRenewAccount.getNum()),
                        autoRenewAccount.getBalance() != null ? autoRenewAccount.getBalance() : 0L))
                .orElse(null);
    }

    private Account getTreasury(EntityId treasuryId, final Optional<Long> timestamp) {
        if (treasuryId == null) {
            return null;
        }
        return timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(treasuryId.getId(), t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(treasuryId.getId()))
                .map(entity -> new Account(
                        entity.getId(),
                        new Id(entity.getShard(), entity.getRealm(), entity.getNum()),
                        entity.getBalance() != null ? entity.getBalance() : 0L))
                .orElse(null);
    }

    private List<CustomFee> getCustomFees(Long tokenId, final Optional<Long> timestamp) {
        return customFeeDatabaseAccessor.get(tokenId, timestamp).orElse(Collections.emptyList());
    }
}
