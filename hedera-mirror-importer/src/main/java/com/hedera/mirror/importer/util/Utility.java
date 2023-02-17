package com.hedera.mirror.importer.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.SECP256K1_EC_UNCOMPRESSED;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.exception.ParserException;

@Log4j2
@UtilityClass
public class Utility {

    public static final Instant MAX_INSTANT_LONG = Instant.ofEpochSecond(0, Long.MAX_VALUE);

    private static final int ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH = 33;

    /**
     * Converts an ECDSA secp256k1 alias to a 20 byte EVM address by taking the keccak hash of it. Logic copied from
     * services' AliasManager.
     *
     * @param alias the bytes representing a serialized Key protobuf
     * @return the 20 byte EVM address
     */
    @SuppressWarnings("java:S1168")
    public static byte[] aliasToEvmAddress(byte[] alias) {
        try {
            if (alias == null ||
                    alias.length != DomainUtils.EVM_ADDRESS_LENGTH
                            && alias.length < ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
                return null;
            }

            if (alias.length == DomainUtils.EVM_ADDRESS_LENGTH) {
                return alias;
            }

            byte[] evmAddress = null;
            var key = Key.parseFrom(alias);
            if (key.getKeyCase() == Key.KeyCase.ECDSA_SECP256K1 &&
                    key.getECDSASecp256K1().size() == ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
                byte[] rawCompressedKey = DomainUtils.toBytes(key.getECDSASecp256K1());
                evmAddress = recoverAddressFromPubKey(rawCompressedKey);
                if (evmAddress == null) {
                    log.warn("Unable to recover EVM address from {}", Hex.encodeHexString(rawCompressedKey));
                }
            }

            return evmAddress;
        } catch (Exception e) {
            var aliasHex = Hex.encodeHexString(alias);
            throw new ParserException("Unable to decode alias to EVM address: " + aliasHex, e);
        }
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
     * Gets epoch day from the timestamp in nanos.
     *
     * @param timestamp The timestamp in nanos
     * @return The epoch day
     */
    public static long getEpochDay(long timestamp) {
        return LocalDate.ofInstant(Instant.ofEpochSecond(0, timestamp), ZoneOffset.UTC)
                .atStartOfDay()
                .toLocalDate()
                .toEpochDay();
    }

    /**
     * Retrieves the nth topic from the contract log info or null if there is no such topic at that index. The topic is
     * returned as a byte array with leading zeros removed.
     *
     * @param contractLoginfo
     * @param index
     * @return a byte array topic with leading zeros removed or null
     */
    @SuppressWarnings("java:S1168")
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

    public static String toSnakeCase(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, text);
    }

    // This method is copied from hedera-services EthTxSigs::recoverAddressFromPubKey and should be kept in sync
    @SuppressWarnings("java:S1168")
    private static byte[] recoverAddressFromPubKey(byte[] pubKeyBytes) {
        LibSecp256k1.secp256k1_pubkey pubKey = new LibSecp256k1.secp256k1_pubkey();
        var parseResult = LibSecp256k1.secp256k1_ec_pubkey_parse(CONTEXT, pubKey, pubKeyBytes, pubKeyBytes.length);
        if (parseResult == 1) {
            return recoverAddressFromPubKey(pubKey);
        } else {
            return null;
        }
    }

    // This method is copied from hedera-services EthTxSigs::recoverAddressFromPubKey and should be kept in sync
    @SuppressWarnings("java:S1191")
    private static byte[] recoverAddressFromPubKey(LibSecp256k1.secp256k1_pubkey pubKey) {
        final ByteBuffer recoveredFullKey = ByteBuffer.allocate(65);
        int value = recoveredFullKey.limit();
        final com.sun.jna.ptr.LongByReference fullKeySize = new com.sun.jna.ptr.LongByReference(value);
        LibSecp256k1.secp256k1_ec_pubkey_serialize(CONTEXT, recoveredFullKey, fullKeySize, pubKey,
                SECP256K1_EC_UNCOMPRESSED);

        recoveredFullKey.get(); // read and discard - recoveryId is not part of the account hash
        var preHash = new byte[64];
        recoveredFullKey.get(preHash, 0, 64);
        var keyHash = new Keccak.Digest256().digest(preHash);
        var address = new byte[20];
        System.arraycopy(keyHash, 12, address, 0, 20);
        return address;
    }
}
