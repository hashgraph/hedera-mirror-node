/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import org.jooq.postgres.extensions.types.LongRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LongRangeConverterTest {

    @CsvSource(
            delimiterString = "#",
            textBlock =
                    """
            1# true# 100# false# [1,100)
            1# true# 100# true# [1,100]
            1# true# # false# [1,)
             # false# 100# true# (,100]
             # false# # false# (,)
            """)
    @ParameterizedTest
    void convert(Long lower, boolean lowerInclusive, Long upper, boolean upperInclusive, String expected) {
        var longRange = LongRange.longRange(lower, lowerInclusive, upper, upperInclusive);
        var expectedRange = PostgreSQLGuavaRangeType.longRange(expected);
        assertThat(LongRangeConverter.INSTANCE.convert(longRange)).isEqualTo(expectedRange);
    }
}
