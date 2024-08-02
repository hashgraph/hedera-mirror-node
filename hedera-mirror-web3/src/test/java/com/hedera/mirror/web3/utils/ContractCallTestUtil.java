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

package com.hedera.mirror.web3.utils;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;

public class ContractCallTestUtil {

    public static final long TRANSACTION_GAS_LIMIT = 15_000_000L;
    public static final ByteString SPENDER_PUBLIC_KEY =
            ByteString.fromHex("3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310");
    public static final Address SPENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SPENDER_PUBLIC_KEY.substring(2).toByteArray())));
    public static final ByteString SENDER_PUBLIC_KEY =
            ByteString.copyFrom(Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
    public static final Address SENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));

    public static final double GAS_ESTIMATE_MULTIPLIER_LOWER_RANGE = 1.05;
    public static final double GAS_ESTIMATE_MULTIPLIER_UPPER_RANGE = 1.2;

    public static final String ESTIMATE_GAS_ERROR_MESSAGE =
            "Expected gas usage to be within the expected range, but it was not. Estimate: %d, Actual: %d";

    public static boolean isWithinExpectedGasRange(final long estimatedGas, final long actualGas) {
        return estimatedGas >= (actualGas * GAS_ESTIMATE_MULTIPLIER_LOWER_RANGE)
                && estimatedGas <= (actualGas * GAS_ESTIMATE_MULTIPLIER_UPPER_RANGE);
    }
}
