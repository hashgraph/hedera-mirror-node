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

package com.hedera.mirror.test.e2e.acceptance.util.ethereum;

import static com.hedera.mirror.test.e2e.acceptance.util.ethereum.EthTxData.EthTransactionType.LEGACY_ETHEREUM;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PrivateKeyECDSA;
import java.math.BigInteger;

public record EthTxSigs(byte[] publicKey, byte[] address) {
    public static EthTxData signMessage(EthTxData ethTx, PrivateKey privateKey) {
        byte[] message = calculateSignableMessage(ethTx);

        final byte[] sig = privateKey.sign(message);
        // wrap in signature object
        final byte[] r = new byte[32];
        System.arraycopy(sig, 0, r, 0, 32);
        final byte[] s = new byte[32];
        System.arraycopy(sig, 32, s, 0, 32);
        int recId = ((PrivateKeyECDSA) privateKey).getRecoveryId(r, s, message);
        BigInteger val;
        // calulations originate from https://eips.ethereum.org/EIPS/eip-155
        if (ethTx.type() == LEGACY_ETHEREUM) {
            if (ethTx.chainId() == null || ethTx.chainId().length == 0) {
                val = BigInteger.valueOf(27L + recId);
            } else {
                val = BigInteger.valueOf(35L + recId).add(new BigInteger(1, ethTx.chainId()).multiply(BigInteger.TWO));
            }
        } else {
            val = null;
        }

        return new EthTxData(
                ethTx.rawTx(),
                ethTx.type(),
                ethTx.chainId(),
                ethTx.nonce(),
                ethTx.gasPrice(),
                ethTx.maxPriorityGas(),
                ethTx.maxGas(),
                ethTx.gasLimit(),
                ethTx.to(),
                ethTx.value(),
                ethTx.callData(),
                ethTx.accessList(),
                (byte) recId,
                val == null ? null : val.toByteArray(),
                r,
                s);
    }

    public static byte[] calculateSignableMessage(EthTxData ethTx) {
        return switch (ethTx.type()) {
            case LEGACY_ETHEREUM -> (ethTx.chainId() != null && ethTx.chainId().length > 0)
                    ? RLPEncoder.list(
                            Integers.toBytes(ethTx.nonce()),
                            ethTx.gasPrice(),
                            Integers.toBytes(ethTx.gasLimit()),
                            ethTx.to(),
                            Integers.toBytesUnsigned(ethTx.value()),
                            ethTx.callData(),
                            ethTx.chainId(),
                            Integers.toBytes(0),
                            Integers.toBytes(0))
                    : RLPEncoder.list(
                            Integers.toBytes(ethTx.nonce()),
                            ethTx.gasPrice(),
                            Integers.toBytes(ethTx.gasLimit()),
                            ethTx.to(),
                            Integers.toBytesUnsigned(ethTx.value()),
                            ethTx.callData());
            case EIP1559 -> RLPEncoder.sequence(Integers.toBytes(2), new Object[] {
                ethTx.chainId(),
                Integers.toBytes(ethTx.nonce()),
                ethTx.maxPriorityGas(),
                ethTx.maxGas(),
                Integers.toBytes(ethTx.gasLimit()),
                ethTx.to(),
                Integers.toBytesUnsigned(ethTx.value()),
                ethTx.callData(),
                new Object[0]
            });
            case EIP2930 -> RLPEncoder.sequence(Integers.toBytes(1), new Object[] {
                ethTx.chainId(),
                Integers.toBytes(ethTx.nonce()),
                ethTx.gasPrice(),
                Integers.toBytes(ethTx.gasLimit()),
                ethTx.to(),
                Integers.toBytesUnsigned(ethTx.value()),
                ethTx.callData(),
                new Object[0]
            });
        };
    }
}
