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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.hedera.mirror.importer.domain.DigestAlgorithm;

public class DigestAlgorithmConverterTest {

    DigestAlgorithmConverter converter = new DigestAlgorithmConverter();

    @Test
    void testForNulls() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @ParameterizedTest
    @EnumSource(DigestAlgorithm.class)
    void testToDatabaseColumn(DigestAlgorithm digestAlgorithm) {
        assertThat(converter.convertToDatabaseColumn(digestAlgorithm)).isEqualTo(digestAlgorithm.getId());
    }

    @ParameterizedTest
    @EnumSource(DigestAlgorithm.class)
    void testToEntityAttribute(DigestAlgorithm digestAlgorithm) {
        assertThat(converter.convertToEntityAttribute(digestAlgorithm.getId())).isEqualTo(digestAlgorithm);
    }

    @ParameterizedTest
    @MethodSource("provideUnknownDigestAlgorithmId")
    void testToEntityAttributeFromUnknownDigestAlgorithmId(int id) {
        assertThat(converter.convertToEntityAttribute(id)).isNull();
    }

    private static Stream<Integer> provideUnknownDigestAlgorithmId() {
        int minId = Integer.MAX_VALUE;
        int maxId = Integer.MIN_VALUE;

        for (DigestAlgorithm digestAlgorithm : DigestAlgorithm.values()) {
            int id = digestAlgorithm.getId();

            if (id > maxId) {
                maxId = digestAlgorithm.getId();
            }

            if (id < minId) {
                minId = digestAlgorithm.getId();
            }
        }

        return Stream.of(minId - 1, maxId + 1);
    }
}
