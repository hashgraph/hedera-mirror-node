/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.Test;

class BlockHashUtilTest {

    @Test
    void testEthHashFromReturnsCorrectValue() {
        final var result = BlockHashUtil.ethHashFrom(
                "37313862636664302d616365352d343861632d396430612d36393036316337656236626333336466323864652d346100");
        final var expected = Hash.wrap(
                Bytes32.wrap(Bytes.fromHexString("0x37313862636664302d616365352d343861632d396430612d3639303631633765")
                        .toArrayUnsafe()));
        assertThat(result).isEqualTo(expected);
    }
}
