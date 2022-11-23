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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AddressValidatorTest {
    private final AddressValidator addressValidator = new AddressValidator();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0x00000000000000000000000000000000000007e7", "0x00000000000000000000000000000000000005E4"
            , "00000000000000000000000000000000000001e8", "00000000000000000000000000000000000001E9"})
    void isValidHappyPath(String value) {
        assertThat(addressValidator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {"0x00000000000000000000000000000000000004e47",
            "0x000000000000000000000000000000Z0000007e7", "0x0000000000000000000000000000000000007e7",
            "000000000000000000000000000000000Z0001e8", "00000000000000000000000000000000000001e",
            "Kp000000000000000000000000000000000001e", "0xzxcvbdfeqrfdseg", "0x000abc", "123589320198", "?$5%.xpo"})
    void isValidNegativeCases(String value) {
        assertThat(addressValidator.isValid(value, null)).isFalse();
    }
}
