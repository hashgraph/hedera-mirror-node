package com.hedera.mirror.importer.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import javax.inject.Named;
import javax.persistence.AttributeConverter;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.util.EntityIdEndec;

@Named
@javax.persistence.Converter
@ConfigurationPropertiesBinding
public class EntityIdConverter implements AttributeConverter<EntityId, Long>, Converter<String, EntityId> {
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
        return EntityIdEndec.decode(encodedId);
    }

    @Override
    public EntityId convert(String source) {
        return EntityId.of(source, EntityTypeEnum.ACCOUNT); // We just use for config properties and don't need type
    }
}
