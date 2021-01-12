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

import javax.inject.Named;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import com.hedera.mirror.importer.domain.DigestAlgorithm;

@Named
@Converter
public class DigestAlgorithmConverter implements AttributeConverter<DigestAlgorithm, Integer> {

    @Override
    public Integer convertToDatabaseColumn(DigestAlgorithm digestAlgorithm) {
        if (digestAlgorithm == null) {
            return null;
        }

        return digestAlgorithm.getId();
    }

    @Override
    public DigestAlgorithm convertToEntityAttribute(Integer digestAlgorithmId) {
        return DigestAlgorithm.from(digestAlgorithmId);
    }
}
