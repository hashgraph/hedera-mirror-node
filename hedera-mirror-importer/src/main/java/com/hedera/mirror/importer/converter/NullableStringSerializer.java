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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.UUID;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Named
public class NullableStringSerializer extends JsonSerializer<String> {
    public static final String NULLABLE_STRING_REPLACEMENT = UUID.randomUUID().toString();

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // empty strings are serialized as null, set to reserved space character and rely on db update sql to parse
        gen.writeString(value.equals("") ? String.valueOf(NULLABLE_STRING_REPLACEMENT) : value);
    }
}
