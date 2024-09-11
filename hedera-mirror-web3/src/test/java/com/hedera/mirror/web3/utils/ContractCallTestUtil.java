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
import com.hederahashgraph.api.proto.java.Key;
import java.util.function.ToLongFunction;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;

@UtilityClass
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
    public static final Long ZERO_VALUE = 0L;
    public static final long CREATE_TOKEN_VALUE = 3070 * 100_000_000L;

    public static final ToLongFunction<String> longValueOf =
            value -> Bytes.fromHexString(value).toLong();
    public static final String LEDGER_ID = "0x03";
    public static final String EMPTY_UNTRIMMED_ADDRESS =
            "0x0000000000000000000000000000000000000000000000000000000000000000";
    public static final byte[] KEY_PROTO = new byte[] {
        58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
        77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };

    public static final byte[] ECDSA_KEY = new byte[] {
        2, 64, 59, -126, 81, -22, 0, 35, 67, -70, 110, 96, 109, 2, -8, 111, -112, -100, -87, -85, 66, 36, 37, -97, 19,
        68, -87, -110, -13, -115, 74, 86, 90
    };

    public static final byte[] ED25519_KEY = new byte[] {
        -128, -61, -12, 63, 3, -45, 108, 34, 61, -2, -83, -48, -118, 20, 84, 85, 85, 67, -125, 46, 49, 26, 17, -116, 27,
        25, 38, -95, 50, 77, 40, -38
    };

    public static final Key KEY_WITH_ECDSA_TYPE =
            Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(ECDSA_KEY)).build();
    public static final Key KEY_WITH_ED_25519_TYPE =
            Key.newBuilder().setEd25519(ByteString.copyFrom(ED25519_KEY)).build();

    public static final long DEFAULT_TOKEN_EXPIRATION_SECONDS = 4_000_000_000L;
    public static final long DEFAULT_TOKEN_EXPIRATION_PERIOD = 8_000_000L;

    public static final long EVM_V_34_BLOCK = 50L;

    /**
     * Checks if the *actual* gas usage is within 5-20% greater than the *expected* gas used from the initial call.
     *
     * @param estimatedGas The expected gas used from the initial call.
     * @param actualGas    The actual gas used.
     * @return {@code true} if the actual gas usage is within the expected range, otherwise {@code false}.
     */
    public static boolean isWithinExpectedGasRange(final long estimatedGas, final long actualGas) {
        return estimatedGas >= (actualGas * GAS_ESTIMATE_MULTIPLIER_LOWER_RANGE)
                && estimatedGas <= (actualGas * GAS_ESTIMATE_MULTIPLIER_UPPER_RANGE);
    }
}
