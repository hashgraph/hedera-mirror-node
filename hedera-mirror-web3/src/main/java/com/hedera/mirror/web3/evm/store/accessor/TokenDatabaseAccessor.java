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

import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.web3.repository.EntityRepository;
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

    @Override
    public @NonNull Optional<Token> get(@NonNull Object address) {
        return entityDatabaseAccessor.get(address).map(this::tokenFromEntity);
    }

    private Token tokenFromEntity(Entity entity) {
        final var databaseToken = tokenRepository.findById(entity.getId()).orElse(null);

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
                Optional.ofNullable(databaseToken.getTotalSupply()).orElse(0L),
                databaseToken.getMaxSupply(),
                parseJkey(databaseToken.getKycKey()),
                parseJkey(databaseToken.getFreezeKey()),
                parseJkey(databaseToken.getSupplyKey()),
                parseJkey(databaseToken.getWipeKey()),
                parseJkey(entity.getKey()),
                parseJkey(databaseToken.getFeeScheduleKey()),
                parseJkey(databaseToken.getPauseKey()),
                Boolean.TRUE.equals(databaseToken.getFreezeDefault()),
                getTreasury(databaseToken.getTreasuryAccountId()),
                getAutoRenewAccount(entity),
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
                getCustomFees(entity.getId()));
    }

    private JKey parseJkey(byte[] keyBytes) {
        try {
            return keyBytes == null ? null : asFcKeyUnchecked(Key.parseFrom(keyBytes));
        } catch (InvalidProtocolBufferException | IllegalArgumentException e) {
            return null;
        }
    }

    private Account getAutoRenewAccount(Entity entity) {
        return entityRepository
                .findByIdAndDeletedIsFalse(entity.getAutoRenewAccountId())
                .map(autoRenewAccount -> new Account(
                        autoRenewAccount.getId(),
                        new Id(autoRenewAccount.getShard(), autoRenewAccount.getRealm(), autoRenewAccount.getNum()),
                        autoRenewAccount.getBalance() != null ? autoRenewAccount.getBalance() : 0L))
                .orElse(null);
    }

    private Account getTreasury(EntityId treasuryId) {
        if (treasuryId == null) {
            return null;
        }
        return entityRepository
                .findByIdAndDeletedIsFalse(treasuryId.getId())
                .map(entity -> new Account(
                        entity.getId(),
                        new Id(entity.getShard(), entity.getRealm(), entity.getNum()),
                        entity.getBalance() != null ? entity.getBalance() : 0L))
                .orElse(null);
    }

    private List<CustomFee> getCustomFees(Long tokenId) {
        return customFeeDatabaseAccessor.get(tokenId).orElse(Collections.emptyList());
    }
}
