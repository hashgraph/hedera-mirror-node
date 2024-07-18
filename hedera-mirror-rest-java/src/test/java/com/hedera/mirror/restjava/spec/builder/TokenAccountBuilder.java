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
import com.hedera.mirror.common.domain.DomainWrapperImpl;
import com.hedera.mirror.common.domain.token.TokenAccount;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionOperations;

@Named
class TokenAccountBuilder extends AbstractEntityBuilder {

    private static final Map<String, Function<Object, Object>> METHOD_PARAMETER_CONVERTERS = Map.of(
            "accountId", ENTITY_ID_TO_LONG_CONVERTER,
            "tokenId", ENTITY_ID_TO_LONG_CONVERTER
    );

    TokenAccountBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
        super(entityManager, transactionOperations, METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    protected void customizeAndPersistEntity(Map<String, Object> account) {
        var builder = TokenAccount.builder();
        // Set defaults
        builder
                .accountId(0L)
                .associated(true)
                .automaticAssociation(false)
                .balance(0L)
                .balanceTimestamp(0L)
                .createdTimestamp(0L)
                .freezeStatus(null)
                .kycStatus(null)
                .timestampRange(null)
                .tokenId(0L);

        // Customize with spec setup definitions
        var wrapper = new DomainWrapperImpl<TokenAccount, TokenAccount.TokenAccountBuilder<?, ?>>(builder, builder::build, entityManager, transactionOperations);
        customizeWithSpec(wrapper, account);

        // Check and finalize
        var entity = wrapper.get();
        if (entity.getTimestampRange() == null) {
            builder.timestampRange(Range.atLeast(entity.getCreatedTimestamp()));
        }

        wrapper.persist();
    }
}
