package com.hedera.mirror.common.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import javax.persistence.AttributeConverter;
import lombok.Getter;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;

public abstract class AbstractEntityIdConverter implements AttributeConverter<EntityId, Long> {

    @Getter
    private final EntityType entityType;

    protected AbstractEntityIdConverter(EntityType entityType) {
        this.entityType = entityType;
    }

    @Override
    public Long convertToDatabaseColumn(EntityId entityId) {
        if (EntityId.isEmpty(entityId)) {
            return null;
        }
        return entityId.getId();
    }

    @Override
    public EntityId convertToEntityAttribute(Long encodedId) {
        if (encodedId == null) {
            return null;
        }
        return EntityIdEndec.decode(encodedId, entityType);
    }
}
