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

package com.hedera.mirror.web3.validation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import jakarta.validation.ConstraintValidatorContext;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaxGasValidatorTest {

    private static final long MAX_GAS = 50_000L;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private ConstraintValidatorContext context;

    private MaxGasValidator maxGasValidator;

    @BeforeEach
    void setMaxGasValidator() {
        maxGasValidator = new MaxGasValidator(mirrorNodeEvmProperties);
    }

    private static Stream<Arguments> provideValidGasValuesForValidation() {
        return Stream.of(Arguments.of(MAX_GAS - 1), Arguments.of(MAX_GAS));
    }

    @ParameterizedTest
    @MethodSource("provideValidGasValuesForValidation")
    void testValidMaxGas(Long gasValue) {
        when(mirrorNodeEvmProperties.getMaxGas()).thenReturn(MAX_GAS);
        assertThat(maxGasValidator.isValid(gasValue, null)).isTrue();
    }

    @Test
    void testInvalidMaxGas() {
        when(mirrorNodeEvmProperties.getMaxGas()).thenReturn(MAX_GAS);
        doNothing().when(context).disableDefaultConstraintViolation();

        ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(any())).thenReturn(violationBuilder);
        when(violationBuilder.addConstraintViolation()).thenReturn(context);

        assertThat(maxGasValidator.isValid(MAX_GAS + 1, context)).isFalse();
    }

    @Test
    void testWithEmptyGas() {
        assertThat(maxGasValidator.isValid(null, null)).isTrue();
    }
}
