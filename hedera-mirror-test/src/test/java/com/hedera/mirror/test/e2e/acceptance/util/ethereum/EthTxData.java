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
import com.google.common.base.MoreObjects;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.binary.Hex;

public record EthTxData(
        byte[] rawTx,
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
                null,
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

    @Override
    public boolean equals(final Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;

        final EthTxData ethTxData = (EthTxData) other;

        return (nonce == ethTxData.nonce)
                && (gasLimit == ethTxData.gasLimit)
                && (recId == ethTxData.recId)
                && (Arrays.equals(rawTx, ethTxData.rawTx))
                && (type == ethTxData.type)
                && (Arrays.equals(chainId, ethTxData.chainId))
                && (Arrays.equals(gasPrice, ethTxData.gasPrice))
                && (Arrays.equals(maxPriorityGas, ethTxData.maxPriorityGas))
                && (Arrays.equals(maxGas, ethTxData.maxGas))
                && (Arrays.equals(to, ethTxData.to))
                && (Objects.equals(value, ethTxData.value))
                && (Arrays.equals(callData, ethTxData.callData))
                && (Arrays.equals(accessList, ethTxData.accessList))
                && (Arrays.equals(v, ethTxData.v))
                && (Arrays.equals(r, ethTxData.r))
                && (Arrays.equals(s, ethTxData.s));
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(rawTx);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(chainId);
        result = 31 * result + (int) (nonce ^ (nonce >>> 32));
        result = 31 * result + Arrays.hashCode(gasPrice);
        result = 31 * result + Arrays.hashCode(maxPriorityGas);
        result = 31 * result + Arrays.hashCode(maxGas);
        result = 31 * result + (int) (gasLimit ^ (gasLimit >>> 32));
        result = 31 * result + Arrays.hashCode(to);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(callData);
        result = 31 * result + Arrays.hashCode(accessList);
        result = 31 * result + recId;
        result = 31 * result + Arrays.hashCode(v);
        result = 31 * result + Arrays.hashCode(r);
        result = 31 * result + Arrays.hashCode(s);
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("rawTx", rawTx == null ? null : Hex.encodeHexString(rawTx))
                .add("type", type)
                .add("chainId", chainId == null ? null : Hex.encodeHexString(chainId))
                .add("nonce", nonce)
                .add("gasPrice", gasPrice == null ? null : Hex.encodeHexString(gasPrice))
                .add("maxPriorityGas", maxPriorityGas == null ? null : Hex.encodeHexString(maxPriorityGas))
                .add("maxGas", maxGas == null ? null : Hex.encodeHexString(maxGas))
                .add("gasLimit", gasLimit)
                .add("to", to == null ? null : Hex.encodeHexString(to))
                .add("value", value)
                .add("callData", Hex.encodeHexString(callData))
                .add("accessList", accessList == null ? null : Hex.encodeHexString(accessList))
                .add("recId", recId)
                .add("v", v == null ? null : Hex.encodeHexString(v))
                .add("r", Hex.encodeHexString(r))
                .add("s", Hex.encodeHexString(s))
                .toString();
    }
}
