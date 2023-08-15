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

package com.hedera.mirror.importer.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.common.converter.ObjectToStringSerializer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;

public class JsonbToListConverter implements GenericConverter {

    private static final ObjectMapper objectMapper = ObjectToStringSerializer.OBJECT_MAPPER;

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Collections.singleton(new ConvertiblePair(PGobject.class, List.class));
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        var elementTypeDescriptor = targetType.getElementTypeDescriptor();
        if (source instanceof PGobject pgo && elementTypeDescriptor != null) {
            var json = pgo.getValue();
            var type =
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementTypeDescriptor.getType());
            try {
                return objectMapper.readValue(json, type);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
