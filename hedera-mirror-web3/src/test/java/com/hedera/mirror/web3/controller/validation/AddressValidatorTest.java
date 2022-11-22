package com.hedera.mirror.web3.controller.validation;

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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import javax.validation.ConstraintValidatorContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.mockito.Mock;

class AddressValidatorTest {
    @Mock
    private ConstraintValidatorContext context;
    private final AddressValidator addressValidator = new AddressValidator();

    @ParameterizedTest
    @CsvFileSource(resources = "/validation/addressValidData.csv", nullValues = {"null"})
    void isValidHappyPath(String value) {
        assertThat(addressValidator.isValid(value, context)).isTrue();
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/validation/addressInvalidData.csv", emptyValue = "empty")
    void isValidNegativeCases(String value) {
        assertThat(addressValidator.isValid(value, context)).isFalse();
    }
}
