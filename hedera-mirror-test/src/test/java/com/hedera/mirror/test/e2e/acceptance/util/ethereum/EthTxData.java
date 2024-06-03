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

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import java.math.BigInteger;
import java.util.List;

public record EthTxData(
        EthTransactionType type,
        byte[] chainId,
        long nonce,
        byte[] gasPrice,
        byte[] maxPriorityGas,
        byte[] maxGas,
        long gasLimit,
        byte[] to,
        BigInteger value,
        byte[] callData,
        byte[] accessList,
        int recId,
        byte[] v,
        byte[] r,
        byte[] s) {

    public EthTxData replaceCallData(final byte[] newCallData) {
        return new EthTxData(
                type,
                chainId,
                nonce,
                gasPrice,
                maxPriorityGas,
                maxGas,
                gasLimit,
                to,
                value,
                newCallData,
                accessList,
                recId,
                v,
                r,
                s);
    }

    public byte[] encodeTx() {
        if (accessList != null && accessList.length > 0) {
            throw new IllegalStateException("Re-encoding access list is unsupported");
        }
        return switch (type) {
            case LEGACY_ETHEREUM -> RLPEncoder.list(
                    Integers.toBytes(nonce),
                    gasPrice,
                    Integers.toBytes(gasLimit),
                    to,
                    Integers.toBytesUnsigned(value),
                    callData,
                    v,
                    r,
                    s);
            case EIP2930 -> RLPEncoder.sequence(
                    Integers.toBytes(0x01),
                    List.of(
                            chainId,
                            Integers.toBytes(nonce),
                            gasPrice,
                            Integers.toBytes(gasLimit),
                            to,
                            Integers.toBytesUnsigned(value),
                            callData,
                            List.of(/*accessList*/ ),
                            Integers.toBytes(recId),
                            r,
                            s));
            case EIP1559 -> RLPEncoder.sequence(
                    Integers.toBytes(0x02),
                    List.of(
                            chainId,
                            Integers.toBytes(nonce),
                            maxPriorityGas,
                            maxGas,
                            Integers.toBytes(gasLimit),
                            to,
                            Integers.toBytesUnsigned(value),
                            callData,
                            List.of(/*accessList*/ ),
                            Integers.toBytes(recId),
                            r,
                            s));
        };
    }

    public enum EthTransactionType {
        LEGACY_ETHEREUM,
        EIP2930,
        EIP1559,
    }
}
