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
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Named
class EntityBuilder extends AbstractEntityBuilder<Entity, Entity.EntityBuilder<?, ?>> {

    private static final Map<String, Function<Object, Object>> METHOD_PARAMETER_CONVERTERS = Map.of(
            "alias", BASE32_CONVERTER,
            "evmAddress", HEX_OR_BASE64_CONVERTER,
            "key", HEX_OR_BASE64_CONVERTER);

    EntityBuilder() {
        super(METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::entities;
    }

    @Override
    protected Entity.EntityBuilder<?, ?> getEntityBuilder() {
        return Entity.builder()
                .declineReward(Boolean.FALSE)
                .deleted(Boolean.FALSE)
                .num(0L)
                .memo("entity memo")
                .num(0L)
                .realm(0L)
                .receiverSigRequired(Boolean.FALSE)
                .shard(0L)
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .timestampRange(Range.atLeast(0L))
                .type(EntityType.ACCOUNT);
    }

    @Override
    protected Entity getFinalEntity(Entity.EntityBuilder<?, ?> builder, Map<String, Object> account) {
        var entity = builder.build();
        if (entity.getId() == null) {
            builder.id(entity.toEntityId().getId());
            entity = builder.build();
        }
        return entity;
    }
}
