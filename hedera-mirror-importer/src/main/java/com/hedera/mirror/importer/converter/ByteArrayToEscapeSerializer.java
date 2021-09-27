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
import java.util.stream.IntStream;
import javax.inject.Named;

/**
 * Implements a byte array to PostgreSQL bytea 'escape' format as
 * <a href="https://www.postgresql.org/docs/current/datatype-binary.html">documented</a>.
 */
@Named
public class ByteArrayToEscapeSerializer extends JsonSerializer<byte[]> {

    public static final ByteArrayToEscapeSerializer INSTANCE = new ByteArrayToEscapeSerializer();
    static final String EMPTY = "\"\"";
    private static final String[] VALUES = new String[256];

    static {
        IntStream.range(0, VALUES.length).forEach(i -> VALUES[i] = String.format("\\%03o", i));
    }

    @Override
    public void serialize(byte[] bytes, JsonGenerator jsonGenerator, SerializerProvider serializers) throws IOException {
        if (bytes != null) {
            if (bytes.length == 0) {
                jsonGenerator.writeRawValue(EMPTY);
                return;
            }

            StringBuilder stringBuilder = new StringBuilder(bytes.length * 2);

            for (byte b : bytes) {
                if (b < 0) {
                    stringBuilder.append(VALUES[b + 256]);
                } else if (b >= 32 && b < 127 && b != '"' && b != '\'' && b != '\\') {
                    stringBuilder.append((char) b);
                } else {
                    stringBuilder.append(VALUES[b]);
                }
            }

            jsonGenerator.writeString(stringBuilder.toString());
        }
    }
}
