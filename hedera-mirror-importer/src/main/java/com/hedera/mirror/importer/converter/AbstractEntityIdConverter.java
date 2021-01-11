package com.hedera.mirror.importer.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import org.springframework.core.convert.converter.Converter;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.util.EntityIdEndec;

public abstract class AbstractEntityIdConverter implements AttributeConverter<EntityId, Long>, Converter<String,
        EntityId> {
    private final EntityTypeEnum entityTypeEnum;

    public AbstractEntityIdConverter(EntityTypeEnum entityTypeEnum) {
        this.entityTypeEnum = entityTypeEnum;
    }

    @Override
    public Long convertToDatabaseColumn(EntityId entityId) {
        if (entityId == null) {
            return null;
        }
        return entityId.getId();
    }

    @Override
    public EntityId convertToEntityAttribute(Long encodedId) {
        if (encodedId == null) {
            return null;
        }
        return EntityIdEndec.decode(encodedId, entityTypeEnum);
    }

    @Override
    public EntityId convert(String source) {
        return EntityId.of(source, entityTypeEnum);
    }
}
