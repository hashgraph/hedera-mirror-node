package com.hedera.mirror.importer.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;

import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.exception.ParserException;

@Log4j2
@UtilityClass
public class Utility {

    public static final Instant MAX_INSTANT_LONG = Instant.ofEpochSecond(0, Long.MAX_VALUE);
    private static final ECCurve SECP256K1_CURVE = new SecP256K1Curve();
    private static final X9IntegerConverter X9_CONVERTER = new X9IntegerConverter();

    /**
     * Converts an ECDSA secp256k1 alias to a 20 byte EVM address by taking the keccak hash of it. Logic copied from
     * services' AliasManager.
     *
     * @param alias the bytes representing a serialized Key protobuf
     * @return the 20 byte EVM address
     */
    public static byte[] aliasToEvmAddress(byte[] alias) {
        try {
            if (alias == null || alias.length == 0) {
                return null;
            }

            var key = Key.parseFrom(alias);

            if (key.getKeyCase() == Key.KeyCase.ECDSA_SECP256K1) {
                var rawCompressedKey = DomainUtils.toBytes(key.getECDSASecp256K1());
                BigInteger x = new BigInteger(rawCompressedKey, 1, 32);
                ECPoint ecPoint = decompressKey(x, (rawCompressedKey[0] & 0x1) == 0x1);
                byte[] uncompressedKeyDer = ecPoint.getEncoded(false);
                byte[] uncompressedKeyRaw = new byte[64];
                System.arraycopy(uncompressedKeyDer, 1, uncompressedKeyRaw, 0, 64);
                byte[] hashedKey = new Keccak.Digest256().digest(uncompressedKeyRaw);

                return Arrays.copyOfRange(hashedKey, 12, 32);
            }

            return null;
        } catch (InvalidProtocolBufferException e) {
            throw new ParserException(e);
        }
    }

    // Decompress a compressed public key (x coordinate and low-bit of y-coordinate).
    private static ECPoint decompressKey(BigInteger x, boolean yBit) {
        final byte[] compEnc = X9_CONVERTER.integerToBytes(x, X9_CONVERTER.getByteLength(SECP256K1_CURVE) + 1);
        compEnc[0] = (byte) (yBit ? 0x03 : 0x02);
        return SECP256K1_CURVE.decodePoint(compEnc);
    }

    /**
     * @return Timestamp from an instant
     */
    public static Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    public static Instant convertToInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    /**
     * print a protobuf Message's content to a String
     *
     * @param message
     * @return
     */
    public static String printProtoMessage(GeneratedMessageV3 message) {
        return TextFormat.shortDebugString(message);
    }

    public static void archiveFile(String filename, byte[] contents, Path destinationRoot) {
        Path destination = destinationRoot.resolve(filename);

        try {
            destination.getParent().toFile().mkdirs();
            Files.write(destination, contents, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.trace("Archived file to {}", destination);
        } catch (Exception e) {
            log.error("Error archiving file to {}", destination, e);
        }
    }

    /**
     * Retrieves the nth topic from the contract log info or null if there is no such topic at that index. The topic is
     * returned as a byte array with leading zeros removed.
     *
     * @param contractLoginfo
     * @param index
     * @return a byte array topic with leading zeros removed or null
     */
    public static byte[] getTopic(ContractLoginfo contractLoginfo, int index) {
        var topics = contractLoginfo.getTopicList();
        ByteString byteString = Iterables.get(topics, index, null);

        if (byteString == null) {
            return null;
        }

        byte[] topic = DomainUtils.toBytes(byteString);
        int firstNonZero = 0;
        for (int i = 0; i < topic.length; i++) {
            if (topic[i] != 0 || i == topic.length - 1) {
                firstNonZero = i;
                break;
            }
        }
        return Arrays.copyOfRange(topic, firstNonZero, topic.length);
    }

    /**
     * Generates a TransactionID object
     *
     * @param payerAccountId the AccountID of the transaction payer account
     */
    public static TransactionID getTransactionId(AccountID payerAccountId) {
        Timestamp validStart = Utility.instantToTimestamp(Instant.now());
        return TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
    }
}
