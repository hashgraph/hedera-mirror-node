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
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.Entity.EntityBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionOperations;

@Named
class AccountBuilder extends AbstractEntityBuilder {

    private static final Map<String, Function<Object, Object>> METHOD_PARAMETER_CONVERTERS = Map.of(
            "alias", BASE32_CONVERTER,
            "evmAddress", HEX_OR_BASE64_CONVERTER,
            "id", ENTITY_ID_CONVERTER
    );

    AccountBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
        super(entityManager, transactionOperations, METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    protected void customizeAndPersistEntity(Map<String, Object> account) {
        var builder = Entity.builder();
        // Set defaults
        builder
                .alias(null)
                .autoRenewAccountId(null)
                .autoRenewAccountId(null)
                .balance(null)
                .balanceTimestamp(null)
                .createdTimestamp(null)
                .declineReward(Boolean.FALSE)
                .deleted(Boolean.FALSE)
                .ethereumNonce(null)
                .evmAddress(null)
                .expirationTimestamp(null)
                .id(null)
                .key(null)
                .maxAutomaticTokenAssociations(0)
                .num(0L)
                .memo("entity memo")
                .num(0L)
                .obtainerId(null)
                .permanentRemoval(null)
                .proxyAccountId(null)
                .publicKey("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f")
                .realm(0L)
                .receiverSigRequired(Boolean.FALSE)
                .shard(0L)
                .stakedAccountId(null)
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .submitKey(null)
                .timestampRange(Range.atLeast(0L))
                .type(EntityType.ACCOUNT);

        // Customize with spec setup definitions
        var wrapper = new DomainWrapperImpl<Entity, EntityBuilder<?, ?>>(builder, builder::build, entityManager, transactionOperations);
        customizeWithSpec(wrapper, account);

        // Check and finalize
        var entity = wrapper.get();
        if (entity.getId() == null) {
            builder.id(EntityId.of(entity.getShard(), entity.getRealm(), entity.getNum()).getId());
        }

        wrapper.persist();
    }
}
