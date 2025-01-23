/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.hedera.mirror.restjava.RestJavaProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NumberRangeParameterTest {

    @Mock
    private RestJavaProperties properties;

    private MockedStatic<SpringApplicationContext> context;

    @BeforeEach
    void setUp() {
        context = Mockito.mockStatic(SpringApplicationContext.class);
        when(SpringApplicationContext.getBean(RestJavaProperties.class)).thenReturn(properties);
    }

    @AfterEach
    void closeMocks() {
        context.close();
    }

    @Test
    void testNoOperatorPresent() {
        assertThat(new NumberRangeParameter(RangeOperator.EQ, 2000L)).isEqualTo(NumberRangeParameter.valueOf("2000"));
    }

    @ParameterizedTest
    @EnumSource(RangeOperator.class)
    void testRangeOperator(RangeOperator operator) {
        assertThat(new NumberRangeParameter(operator, 2000L))
                .isEqualTo(NumberRangeParameter.valueOf(operator + ":2000"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testEmpty(String input) {
        assertThat(NumberRangeParameter.valueOf(input)).isEqualTo(NumberRangeParameter.EMPTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", ".1", "someinvalidstring", "-1", "9223372036854775808", ":2000", ":", "eq:", ":1"})
    @DisplayName("IntegerRangeParameter parse from string tests, negative cases")
    void testInvalidParam(String input) {
        assertThrows(IllegalArgumentException.class, () -> NumberRangeParameter.valueOf(input));
    }
}
