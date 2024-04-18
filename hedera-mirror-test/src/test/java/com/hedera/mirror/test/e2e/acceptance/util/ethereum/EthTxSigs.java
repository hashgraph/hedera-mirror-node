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
import java.math.BigInteger;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECPoint;

public record EthTxSigs(byte[] publicKey, byte[] address) {

    static final X9ECParameters ECDSA_SECP256K1_CURVE = SECNamedCurves.getByName("secp256k1");
    static final ECDomainParameters ECDSA_SECP256K1_DOMAIN = new ECDomainParameters(
            ECDSA_SECP256K1_CURVE.getCurve(),
            ECDSA_SECP256K1_CURVE.getG(),
            ECDSA_SECP256K1_CURVE.getN(),
            ECDSA_SECP256K1_CURVE.getH());

    public static EthTxData signMessage(EthTxData ethTx, PrivateKey privateKey) {
        byte[] message = calculateSignableMessage(ethTx);

        final byte[] sig = privateKey.sign(message);
        // wrap in signature object
        final byte[] r = new byte[32];
        System.arraycopy(sig, 0, r, 0, 32);
        final byte[] s = new byte[32];
        System.arraycopy(sig, 32, s, 0, 32);

        // FUTURE - this part of recId calculation is not present in the SDK, we should move it there
        var hash = new Keccak.Digest256().digest(message);
        int recId = 0;
        var publicKey = privateKey.getPublicKey().toBytesRaw();
        for (int i = 0; i < 4; i++) {
            byte[] k = recoverFromSignature(i, new BigInteger(1, r), new BigInteger(1, s), Bytes32.wrap(hash));
            if (k != null && Arrays.equals(k, publicKey)) {
                recId = i;
                break;
            }
        }

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

    /**
     * Given the components of a signature and a selector value, recover and return the public key that generated the
     * signature according to the algorithm in SEC1v2 section 4.1.6.
     *
     * @param recId Which possible key to recover.
     * @param r The R component of the signature.
     * @param s The S component of the signature.
     * @param messageHash Hash of the data that was signed.
     * @return A ECKey containing only the public part, or {@code null} if recovery wasn't possible.
     */
    public static byte[] recoverFromSignature(int recId, BigInteger r, BigInteger s, Bytes32 messageHash) {
        assert (recId == 0 || recId == 1);
        assert (r.signum() >= 0);
        assert (s.signum() >= 0);
        assert (messageHash != null);

        ECPoint R = decompressKey(r, (recId & 1) == 1);
        if (R == null || !R.multiply(ECDSA_SECP256K1_DOMAIN.getN()).isInfinity()) {
            return null;
        }

        BigInteger e = messageHash.toUnsignedBigInteger();
        BigInteger eInv = BigInteger.ZERO.subtract(e).mod(ECDSA_SECP256K1_DOMAIN.getN());
        BigInteger rInv = r.modInverse(ECDSA_SECP256K1_DOMAIN.getN());
        BigInteger srInv = rInv.multiply(s).mod(ECDSA_SECP256K1_DOMAIN.getN());
        BigInteger eInvrInv = rInv.multiply(eInv).mod(ECDSA_SECP256K1_DOMAIN.getN());
        ECPoint q = ECAlgorithms.sumOfTwoMultiplies(ECDSA_SECP256K1_DOMAIN.getG(), eInvrInv, R, srInv);

        if (q.isInfinity()) {
            return null;
        }

        return q.getEncoded(true);
    }

    private static ECPoint decompressKey(BigInteger xBN, boolean yBit) {
        var X_9_INTEGER_CONVERTER = new X9IntegerConverter();
        byte[] compEnc = X_9_INTEGER_CONVERTER.integerToBytes(
                xBN, 1 + X_9_INTEGER_CONVERTER.getByteLength(ECDSA_SECP256K1_DOMAIN.getCurve()));
        compEnc[0] = (byte) (yBit ? 0x03 : 0x02);
        try {
            return ECDSA_SECP256K1_DOMAIN.getCurve().decodePoint(compEnc);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
