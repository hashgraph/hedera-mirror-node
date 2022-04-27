package com.hedera.mirror.common.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import java.math.BigInteger;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;

public class ByteArrayWeiBarToStringSerializer extends JsonSerializer<byte[]> {
    static final ByteArrayWeiBarToStringSerializer INSTANCE = new ByteArrayWeiBarToStringSerializer();
    static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);
    static final String PREFIX = "\\x";

    @Override
    public void serialize(byte[] weibar, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (!ArrayUtils.isEmpty(weibar)) {
            // convert weibar to tinybar
            gen.writeNumber(PREFIX +
                    Hex.encodeHexString(new BigInteger(weibar).divide(WEIBARS_TO_TINYBARS).toByteArray()));
        } else {
            gen.writeNull();
        }
    }
}
