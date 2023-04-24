/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.ethereum;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import com.google.common.base.MoreObjects;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
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

    public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);


    public static EthTxData populateEthTxData(byte[] data) {
        try {
            var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(data);
            var rlpItem = decoder.next();
            EthTransactionType type;
            long nonce;
            byte[] chainId = null;
            byte[] gasPrice = null;
            byte[] maxPriorityGas = null;
            byte[] maxGas = null;
            long gasLimit;
            byte[] to;
            BigInteger value;
            byte[] callData;
            byte[] accessList = null;
            byte recId;
            byte[] v = null;
            byte[] r;
            byte[] s;
            if (rlpItem.isList()) {
                // frontier TX
                type = EthTransactionType.LEGACY_ETHEREUM;
                List<RLPItem> rlpList = rlpItem.asRLPList().elements();
                if (rlpList.size() != 9) {
                    return null;
                }
                nonce = rlpList.get(0).asLong();
                gasPrice = rlpList.get(1).asBytes();
                gasLimit = rlpList.get(2).asLong();
                to = rlpList.get(3).data();
                value = rlpList.get(4).asBigInt();
                callData = rlpList.get(5).data();
                v = rlpList.get(6).asBytes();
                BigInteger vBI = new BigInteger(1, v);
                recId = vBI.testBit(0) ? (byte) 0 : 1;
                r = rlpList.get(7).data();
                s = rlpList.get(8).data();
                if (vBI.compareTo(BigInteger.valueOf(34)) > 0) {
                    chainId = vBI.subtract(BigInteger.valueOf(35)).shiftRight(1).toByteArray();
                }
            } else {
                // typed transaction?
                byte typeByte = rlpItem.asByte();
                if (typeByte != 2) {
                    // we only support EIP1559 at the moment.
                    return null;
                }
                type = EthTransactionType.EIP1559;
                rlpItem = decoder.next();
                if (!rlpItem.isList()) {
                    return null;
                }
                List<RLPItem> rlpList = rlpItem.asRLPList().elements();
                if (rlpList.size() != 12) {
                    return null;
                }
                chainId = rlpList.get(0).data();
                nonce = rlpList.get(1).asLong();
                maxPriorityGas = rlpList.get(2).data();
                maxGas = rlpList.get(3).data();
                gasLimit = rlpList.get(4).asLong();
                to = rlpList.get(5).data();
                value = rlpList.get(6).asBigInt();
                callData = rlpList.get(7).data();
                accessList = rlpList.get(8).data();
                recId = rlpList.get(9).asByte();
                r = rlpList.get(10).data();
                s = rlpList.get(11).data();
            }

            return new EthTxData(
                    data,
                    type,
                    chainId,
                    nonce,
                    gasPrice,
                    maxPriorityGas,
                    maxGas,
                    gasLimit,
                    to,
                    value,
                    callData,
                    accessList,
                    recId,
                    v,
                    r,
                    s);
        } catch (IllegalArgumentException | NoSuchElementException e) {
            return null;
        }
    }

    public long getAmount() {
        return value.divide(WEIBARS_TO_TINYBARS).longValueExact();
    }

    public enum EthTransactionType {
        LEGACY_ETHEREUM,
        EIP2930,
        EIP1559,
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final EthTxData ethTxData = (EthTxData) o;

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
