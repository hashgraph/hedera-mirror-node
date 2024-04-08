package com.hedera.mirror.test.e2e.acceptance.client;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import java.math.BigInteger;
import java.util.List;

// TODO: move
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
    
}