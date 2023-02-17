package com.hedera.mirror.web3.convert;

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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BytesDecoderTest {

    @CsvSource(nullValues = "null", textBlock = """
              0x08c379a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000047465737400000000000000000000000000000000000000000000000000000000,                                                                                         test
              0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000,                                                                                         Custom revert message
              0x08c379a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000304d65737361676520776974682073796d626f6c7320616e64206e756d62657273202d2021203120322033202a2026203f00000000000000000000000000000000,                         Message with symbols and numbers - ! 1 2 3 * & ?
              000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000047465737400000000000000000000000000000000000000000000000000000000,                                                                                                   test
              00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000,                                                                                                   Custom revert message
              000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000304d65737361676520776974682073796d626f6c7320616e64206e756d62657273202d2021203120322033202a2026203f00000000000000000000000000000000,                                   Message with symbols and numbers - ! 1 2 3 * & ?
            """)
    @ParameterizedTest
    void convertBytes(String input, String output) {
        assertThat(BytesDecoder.decodeEvmRevertReasonBytesToReadableMessage(Bytes.fromHexString(input))).isEqualTo(output);
    }
}
