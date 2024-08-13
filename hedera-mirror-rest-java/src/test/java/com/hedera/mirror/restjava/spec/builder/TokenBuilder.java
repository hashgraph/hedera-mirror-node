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

package com.hedera.mirror.restjava.spec.builder;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
class TokenBuilder extends AbstractEntityBuilder<Token.TokenBuilder<?, ?>> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::tokens;
    }

    @Override
    protected Token.TokenBuilder<?, ?> getEntityBuilder() {
        return Token.builder()
                .createdTimestamp(0L)
                .decimals(1000)
                .freezeDefault(Boolean.FALSE)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .initialSupply(1_000_000L)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .maxSupply(Long.MAX_VALUE)
                .metadata(DomainUtils.EMPTY_BYTE_ARRAY)
                .name("Token name")
                .pauseStatus(TokenPauseStatusEnum.NOT_APPLICABLE)
                .supplyType(TokenSupplyTypeEnum.INFINITE)
                .symbol("YBTJBOAZ")
                .tokenId(0L)
                .totalSupply(1_000_000L)
                .treasuryAccountId(EntityId.of(98L))
                .type(TokenTypeEnum.FUNGIBLE_COMMON);
    }

    @Override
    protected List<Object> getFinalEntities(Token.TokenBuilder<?, ?> builder, Map<String, Object> token) {
        var tokenEntity = builder.build();
        if (tokenEntity.getType() == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            builder.decimals(0);
            builder.initialSupply(0L);
        }

        if (tokenEntity.getTimestampRange() == null) {
            builder.timestampRange(Range.atLeast(tokenEntity.getCreatedTimestamp()));
        }

        return List.of(builder.build());
    }
}
