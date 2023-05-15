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
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.Collections;
import java.util.Optional;
import javax.inject.Named;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;

@Named
@RequiredArgsConstructor
public class TokenDatabaseAccessor extends DatabaseAccessor<Address, Token> {

    private final TokenRepository tokenRepository;

    private final EntityDatabaseAccessor entityDatabaseAccessor;

    @Override
    public @NonNull Optional<Token> get(@NonNull Address address) {
        return entityDatabaseAccessor.get(address).map(this::tokenFromEntity);
    }

    private Token tokenFromEntity(Entity entity) {
        final var databaseToken =
                tokenRepository.findById(new TokenId(entity.toEntityId())).orElse(null);

        if (databaseToken == null) {
            return null;
        }
        try {
            return new Token(
                    new Id(entity.getShard(), entity.getRealm(), entity.getNum()),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    false,
                    TokenType.valueOf(databaseToken.getType().name()),
                    TokenSupplyType.valueOf(databaseToken.getSupplyType().name()),
                    databaseToken.getTotalSupply(),
                    databaseToken.getMaxSupply(),
                    asFcKeyUnchecked(Key.parseFrom(databaseToken.getKycKey())),
                    asFcKeyUnchecked(Key.parseFrom(databaseToken.getFreezeKey())),
                    asFcKeyUnchecked(Key.parseFrom(databaseToken.getSupplyKey())),
                    asFcKeyUnchecked(Key.parseFrom(databaseToken.getWipeKey())),
                    asFcKeyUnchecked(Key.parseFrom(entity.getKey())),
                    asFcKeyUnchecked(Key.parseFrom(databaseToken.getFeeScheduleKey())),
                    asFcKeyUnchecked(Key.parseFrom(databaseToken.getPauseKey())),
                    databaseToken.getFreezeDefault(),
                    null,
                    null,
                    entity.getDeleted(),
                    databaseToken.getPauseStatus().equals(TokenPauseStatusEnum.PAUSED),
                    false,
                    entity.getExpirationTimestamp(),
                    false,
                    entity.getMemo(),
                    databaseToken.getName(),
                    databaseToken.getSymbol(),
                    databaseToken.getDecimals(),
                    entity.getAutoRenewPeriod(),
                    0L);
        } catch (InvalidProtocolBufferException e) {
            return null;
        }
    }
}
