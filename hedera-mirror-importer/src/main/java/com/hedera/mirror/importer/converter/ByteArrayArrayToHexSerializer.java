/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.apache.commons.codec.binary.Hex;

@SuppressWarnings("java:S6548")
public class ByteArrayArrayToHexSerializer extends JsonSerializer<byte[][]> {

    public static final ByteArrayArrayToHexSerializer INSTANCE = new ByteArrayArrayToHexSerializer();

    private static final String DELIMITER = ",";
    private static final String END = "}";
    private static final String NULL = "null";
    private static final String PREFIX = "\\\\x";
    private static final String QUOTE = "\"";
    private static final String START = "{";

    private ByteArrayArrayToHexSerializer() {}

    @Override
    public void serialize(byte[][] value, JsonGenerator jsonGenerator, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            return;
        }

        var sb = new StringBuilder();
        sb.append(START);
        for (int i = 0; i < value.length; i++) {
            byte[] elem = value[i];
            if (elem != null) {
                sb.append(QUOTE);
                sb.append(PREFIX);
                sb.append(Hex.encodeHexString(elem));
                sb.append(QUOTE);
            } else {
                sb.append(NULL);
            }

            if (i < value.length - 1) {
                sb.append(DELIMITER);
            }
        }
        sb.append(END);

        jsonGenerator.writeString(sb.toString());
    }
}
