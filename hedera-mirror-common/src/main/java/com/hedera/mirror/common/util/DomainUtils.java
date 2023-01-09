package com.hedera.mirror.common.util;

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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;

import com.google.protobuf.ByteOutput;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.common.exception.ProtobufException;
import com.hedera.services.stream.proto.HashObject;

@CustomLog
@UtilityClass
public class DomainUtils {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final int EVM_ADDRESS_LENGTH = 20;
    public static final long TINYBARS_IN_ONE_HBAR = 100_000_000L;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final char NULL_CHARACTER = (char) 0;
    private static final char NULL_REPLACEMENT = '�'; // Standard replacement character 0xFFFD

    /**
     * Convert bytes to hex.
     *
     * @param bytes to be converted
     * @return converted HexString
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        return ArrayUtils.isNotEmpty(bytes) ? Hex.encodeHexString(bytes) : "";
    }

    public static byte[] getHashBytes(HashObject hashObject) {
        return toBytes(hashObject.getHash());
    }

    /**
     * A key can either be a complex key (e.g. key list or threshold key) or a primitive key (e.g. ED25519 or
     * ECDSA_SECP256K1). If the protobuf encoding of a Key is a single primitive key or a complex key with exactly one
     * primitive key within it, return the key as a String with lowercase hex encoding.
     *
     * @param protobufKey
     * @return public key as a string in hex encoding, or null
     */
    public static String getPublicKey(@Nullable byte[] protobufKey) {
        try {
            if (protobufKey == null) {
                return null;
            }

            if (ArrayUtils.isEmpty(protobufKey)) {
                return ""; // Key.getDefaultInstance() case
            }

            Key key = Key.parseFrom(protobufKey);
            byte[] primitiveKey = getPublicKey(key, 1);
            return bytesToHex(primitiveKey);
        } catch (Exception e) {
            log.error("Unable to parse protobuf Key", e);
            return null;
        }
    }

    @SuppressWarnings("java:S1168")
    private static byte[] getPublicKey(Key key, int depth) {
        // We don't support searching for primitive keys at multiple levels since the REST API matches by hex prefix
        if (depth > 2) {
            return null;
        }

        return switch (key.getKeyCase()) {
            case ECDSA_384 -> toBytes(key.getECDSA384());
            case ECDSA_SECP256K1 -> toBytes(key.getECDSASecp256K1());
            case ED25519 -> toBytes(key.getEd25519());
            case KEYLIST -> getPublicKey(key.getKeyList(), depth);
            case RSA_3072 -> toBytes(key.getRSA3072());
            case THRESHOLDKEY -> getPublicKey(key.getThresholdKey().getKeys(), depth);
            default -> null;
        };
    }

    @SuppressWarnings("java:S1168")
    private static byte[] getPublicKey(KeyList keyList, int depth) {
        List<Key> keys = keyList.getKeysList();
        if (keys.size() == 1) {
            return getPublicKey(keys.get(0), depth + 1);
        }
        return null;
    }

    /**
     * Converts time in (second, nanos) to time in only nanos.
     */
    public static long convertToNanos(long second, long nanos) {
        try {
            return Math.addExact(Math.multiplyExact(second, NANOS_PER_SECOND), nanos);
        } catch (ArithmeticException e) {
            log.error("Long overflow when converting time to nanos timestamp : {}s {}ns", second, nanos);
            throw e;
        }
    }

    /**
     * Converts time in (second, nanos) to time in only nanos, with a fallback if overflow: If positive overflow, return
     * the max time in the future (Long.MAX_VALUE). If negative overflow, return the max time in the past
     * (Long.MIN_VALUE).
     */
    public static long convertToNanosMax(long second, long nanos) {
        try {
            return convertToNanos(second, nanos);
        } catch (ArithmeticException ex) {
            return second >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    /**
     * Converts instant to time in only nanos, with a fallback if overflow: If positive overflow, return the max time in
     * the future (Long.MAX_VALUE). If negative overflow, return the max time in the past (Long.MIN_VALUE).
     */
    public static long convertToNanosMax(Instant instant) {
        if (instant == null) {
            instant = Instant.EPOCH;
        }

        return convertToNanosMax(instant.getEpochSecond(), instant.getNano());
    }

    /**
     * Pad a byte array with leading zeros to a given length.
     * @param bytes
     * @param length
     */
    public static byte[] leftPadBytes(byte[] bytes, int length) {
        if (bytes == null) {
            return EMPTY_BYTE_ARRAY;
        }

        int paddingSize = length - bytes.length;
        if (paddingSize <= 0) {
            return bytes;
        }

        var leftPaddedBytes = new byte[length];
        System.arraycopy(bytes, 0, leftPaddedBytes, paddingSize, bytes.length);
        return leftPaddedBytes;
    }

    /**
     * Convert Timestamp to a Long type timeStampInNanos
     */
    public static Long timeStampInNanos(Timestamp timestamp) {
        try {
            if (timestamp == null) {
                return null;
            }
            return Math.addExact(Math.multiplyExact(timestamp.getSeconds(), NANOS_PER_SECOND), timestamp.getNanos());
        } catch (ArithmeticException e) {
            throw new ArithmeticException("Long overflow when converting Timestamp to nanos timestamp: " + timestamp);
        }
    }

    public static Long timestampInNanosMax(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return convertToNanosMax(timestamp.getSeconds(), timestamp.getNanos());
    }

    /**
     * Cleans a string of invalid characters that would cause it to fail when inserted into the database. In particular,
     * PostgreSQL does not allow the null character (0x0000) to be inserted.
     *
     * @param input string containing potentially invalid characters
     * @return the cleaned string
     */
    public static String sanitize(String input) {
        return StringUtils.isNotEmpty(input) ? input.replace(NULL_CHARACTER, NULL_REPLACEMENT) : input;
    }

    /**
     * This method converts a protobuf ByteString into a byte array. Optimization is done in case the input is a
     * LiteralByteString to not make a copy of the underlying array and return it as is. This is okay for our purposes
     * since we never modify the array and just directly store it in the database.
     * <p>
     * If the ByteString is smaller than the estimated size to allocate an UnsafeByteOutput object, copy the array
     * regardless since we'd be allocating a similar amount of memory either way.
     *
     * @param byteString to convert
     * @return bytes extracted from the ByteString
     */
    @SuppressWarnings("java:S1168")
    public static byte[] toBytes(ByteString byteString) {
        if (byteString == null) {
            return null;
        }

        try {
            if (UnsafeByteOutput.supports(byteString)) {
                UnsafeByteOutput byteOutput = new UnsafeByteOutput();
                UnsafeByteOperations.unsafeWriteTo(byteString, byteOutput);
                return byteOutput.bytes;
            }
        } catch (IOException e) {
            log.warn("Unsafe retrieval of bytes failed", e);
        }

        return byteString.toByteArray();
    }

    public static ByteString fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        return UnsafeByteOperations.unsafeWrap(bytes);
    }

    // The 'shard.realm.num' form evm address has 4 bytes for shard, and 8 bytes each for realm and num.
    public static EntityId fromEvmAddress(byte[] evmAddress) {
        try {
            if (evmAddress != null && evmAddress.length == EVM_ADDRESS_LENGTH) {
                ByteBuffer buffer = ByteBuffer.wrap(evmAddress);
                return EntityId.of(buffer.getInt(), buffer.getLong(), buffer.getLong(), CONTRACT);
            }
        } catch (InvalidEntityException ex) {
            log.debug("Failed to parse shard.realm.num form evm address into EntityId", ex);
        }
        return null;
    }

    public static byte[] toEvmAddress(ContractID contractId) {
        if (contractId == null || contractId == ContractID.getDefaultInstance()) {
            throw new InvalidEntityException("Invalid ContractID");
        }

        if (contractId.getContractCase() == ContractID.ContractCase.EVM_ADDRESS) {
            return toBytes(contractId.getEvmAddress());
        }

        return toEvmAddress((int) contractId.getShardNum(), contractId.getRealmNum(), contractId.getContractNum());
    }

    public static byte[] toEvmAddress(EntityId contractId) {
        if (EntityId.isEmpty(contractId)) {
            throw new InvalidEntityException("Empty contractId");
        }

        return toEvmAddress(contractId.getShardNum().intValue(), contractId.getRealmNum(), contractId.getEntityNum());
    }

    private static byte[] toEvmAddress(int shard, long realm, long num) {
        byte[] evmAddress = new byte[EVM_ADDRESS_LENGTH];
        ByteBuffer buffer = ByteBuffer.wrap(evmAddress);
        buffer.putInt(shard);
        buffer.putLong(realm);
        buffer.putLong(num);
        return evmAddress;
    }

    static class UnsafeByteOutput extends ByteOutput {

        static final short SIZE = 12 + 4; // Size of the object header plus a compressed object reference to bytes field
        private static final Class<?> SUPPORTED_CLASS;

        static {
            try {
                SUPPORTED_CLASS = Class.forName(ByteString.class.getName() + "$LiteralByteString");
            } catch (ClassNotFoundException e) {
                throw new ProtobufException(String.format("Unable to locate class=%s", ByteString.class.getName() + "$LiteralByteString"));
            }
        }

        private byte[] bytes;

        private static boolean supports(ByteString byteString) {
            return byteString.size() > UnsafeByteOutput.SIZE && byteString.getClass() == UnsafeByteOutput.SUPPORTED_CLASS;
        }

        @Override
        public void write(byte value) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            this.bytes = bytes;
        }

        @Override
        public void writeLazy(byte[] bytes, int offset, int length) throws IOException {
            this.bytes = bytes;
        }

        @Override
        public void write(ByteBuffer value) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeLazy(ByteBuffer value) throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}
