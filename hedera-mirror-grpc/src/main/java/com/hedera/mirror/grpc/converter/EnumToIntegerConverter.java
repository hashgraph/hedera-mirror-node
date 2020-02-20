package com.hedera.mirror.grpc.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@Named
@WritingConverter
public class EnumToIntegerConverter<T extends Enum<?>> implements Converter<T, Integer> {

    @Override
    public Integer convert(T enumeration) {
        return enumeration != null ? enumeration.ordinal() : null;
    }
}
