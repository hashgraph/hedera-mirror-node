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

import java.nio.charset.Charset;
import javax.inject.Named;
import javax.persistence.AttributeConverter;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;

@Named
@javax.persistence.Converter
@ConfigurationPropertiesBinding
public class StringToByteArrayConverter implements AttributeConverter<String, byte[]> {
    @Override
    public byte[] convertToDatabaseColumn(String memo) {
        if (memo == null) {
            return null;
        }
        return memo.getBytes();
    }

    @Override
    public String convertToEntityAttribute(byte[] memoBytes) {
        if (memoBytes == null) {
            return null;
        }
        return new String(memoBytes, Charset.forName("UTF-8"));
    }
}
