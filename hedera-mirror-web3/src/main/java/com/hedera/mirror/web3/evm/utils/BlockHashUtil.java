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

import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

@UtilityClass
public class BlockHashUtil {

    public static org.hyperledger.besu.datatypes.Hash ethHashFrom(final String hash) {
        final byte[] hashBytesToConvert = Bytes.fromHexString(hash).toArrayUnsafe();
        final byte[] prefixBytes = new byte[32];
        System.arraycopy(hashBytesToConvert, 0, prefixBytes, 0, 32);
        return org.hyperledger.besu.datatypes.Hash.wrap(Bytes32.wrap(prefixBytes));
    }
}
