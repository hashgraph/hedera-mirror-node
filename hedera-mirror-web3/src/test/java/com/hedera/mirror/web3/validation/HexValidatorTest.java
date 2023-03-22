package com.hedera.mirror.web3.validation;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.lang.annotation.Annotation;
import javax.validation.Payload;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HexValidatorTest {

    private final HexValidator hexValidator = new HexValidator();

    @ParameterizedTest
    @CsvSource({
            "0,0,''",
            "1,3,0xa",
            "1,3,0xa1D",
            "1,3,a",
            "1,3,a1D",
            "40,40,",
            "40,40,0x00000000000000000000000000000000000007e7",
            "40,40,0x00000000000000000000000000000000000005E4",
            "40,40,00000000000000000000000000000000000001e8",
            "40,40,00000000000000000000000000000000000001E9"
    })
    void valid(int minLength, int maxLength, String value) {
        hexValidator.initialize(hex(minLength, maxLength));
        assertThat(hexValidator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,1",
            "0,0,0x",
            "1,3,''",
            "1,3,0xa1D4",
            "2,3,a",
            "1,3,a1D4",
            "40,40,''",
            "40,40,0x00000000000000000000000000000000000004e47",
            "40,40,0x000000000000000000000000000000Z0000007e7",
            "40,40,0x0000000000000000000000000000000000007e7",
            "40,40,000000000000000000000000000000000Z0001e8",
            "40,40,00000000000000000000000000000000000001e",
            "40,40,Kp000000000000000000000000000000000001e",
            "40,40,0xzxcvbdfeqrfdseg",
            "40,40,0x000abc",
            "40,40,123589320198",
            "40,40,?$5%.xpo"
    })
    void invalid(int minLength, int maxLength, String value) {
        hexValidator.initialize(hex(minLength, maxLength));
        assertThat(hexValidator.isValid(value, null)).isFalse();
    }

    private Hex hex(int minLength, int maxLength) {
        return new Hex() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return null;
            }

            @Override
            public String message() {
                return null;
            }

            @Override
            public Class<?>[] groups() {
                return new Class[0];
            }

            @Override
            public Class<? extends Payload>[] payload() {
                return null;
            }

            @Override
            public long maxLength() {
                return maxLength;
            }

            @Override
            public long minLength() {
                return minLength;
            }
        };
    }
}
