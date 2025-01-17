/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
class TokenAccountBuilder extends AbstractEntityBuilder<TokenAccount, TokenAccount.TokenAccountBuilder<?, ?>> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::tokenAccounts;
    }

    @Override
    protected TokenAccount.TokenAccountBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        return TokenAccount.builder()
                .accountId(0L)
                .associated(Boolean.TRUE)
                .automaticAssociation(Boolean.FALSE)
                .balance(0L)
                .balanceTimestamp(0L)
                .createdTimestamp(0L)
                .tokenId(0L);
    }

    @Override
    protected TokenAccount getFinalEntity(TokenAccount.TokenAccountBuilder<?, ?> builder, Map<String, Object> account) {
        var entity = builder.build();
        if (entity.getTimestampRange() == null) {
            builder.timestampRange(Range.atLeast(entity.getCreatedTimestamp()));
            entity = builder.build();
        }
        return entity;
    }
}
