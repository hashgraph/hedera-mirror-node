package com.hedera.mirror.grpc.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.Instant;

public class LongToInstantDeserializer extends StdDeserializer<Instant> {

    private static final long serialVersionUID = -8215882906184345545L;
    private static final LongToInstantConverter longToInstantConverter = new LongToInstantConverter();

    public LongToInstantDeserializer() {
        super(Instant.class);
    }

    @Override
    public Instant deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException,
            JsonProcessingException {
        return longToInstantConverter.convert(jsonParser.getLongValue());
    }
}
