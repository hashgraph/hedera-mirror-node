/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.utils;

import static com.hedera.mirror.web3.evm.account.AccountAccessorImpl.EVM_ADDRESS_SIZE;
import static com.hedera.services.utils.BitPackUtils.numFromCode;
import static java.lang.System.arraycopy;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public final class EntityIdUtils {
    private static final String CANNOT_PARSE_PREFIX = "Cannot parse '";

    private EntityIdUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static ContractID asContract(final AccountID id) {
        return ContractID.newBuilder()
                .setRealmNum(id.getRealmNum())
                .setShardNum(id.getShardNum())
                .setContractNum(id.getAccountNum())
                .build();
    }

    public static AccountID asAccount(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return AccountID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setAccountNum(nativeParts[2])
                .build();
    }

    public static ContractID asContract(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return ContractID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setContractNum(nativeParts[2])
                .build();
    }

    public static TokenID asToken(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return TokenID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setTokenNum(nativeParts[2])
                .build();
    }

    public static byte[] asEvmAddress(final ContractID id) {
        if (id.getEvmAddress().size() == EVM_ADDRESS_SIZE) {
            return id.getEvmAddress().toByteArray();
        } else {
            return asEvmAddress((int) id.getShardNum(), id.getRealmNum(), id.getContractNum());
        }
    }

    public static Id asModelId(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return new Id(nativeParts[0], nativeParts[1], nativeParts[2]);
    }

    public static byte[] asEvmAddress(final AccountID id) {
        return asEvmAddress((int) id.getShardNum(), id.getRealmNum(), id.getAccountNum());
    }

    public static byte[] asEvmAddress(final TokenID id) {
        return asEvmAddress((int) id.getShardNum(), id.getRealmNum(), id.getTokenNum());
    }

    public static byte[] asEvmAddress(final int shard, final long realm, final long num) {
        final byte[] evmAddress = new byte[20];

        arraycopy(Ints.toByteArray(shard), 0, evmAddress, 0, 4);
        arraycopy(Longs.toByteArray(realm), 0, evmAddress, 4, 8);
        arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);

        return evmAddress;
    }

    public static byte[] asEvmAddress(final long num) {
        final byte[] evmAddress = new byte[20];
        arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);
        return evmAddress;
    }

    public static long shardFromEvmAddress(final byte[] bytes) {
        return Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4));
    }

    public static long realmFromEvmAddress(final byte[] bytes) {
        return Longs.fromByteArray(Arrays.copyOfRange(bytes, 4, 12));
    }

    public static long numFromEvmAddress(final byte[] bytes) {
        return Longs.fromBytes(bytes[12], bytes[13], bytes[14], bytes[15], bytes[16], bytes[17], bytes[18], bytes[19]);
    }

    public static AccountID accountIdFromEvmAddress(final byte[] bytes) {
        return AccountID.newBuilder()
                .setShardNum(shardFromEvmAddress(bytes))
                .setRealmNum(realmFromEvmAddress(bytes))
                .setAccountNum(numFromEvmAddress(bytes))
                .build();
    }

    public static ContractID contractIdFromEvmAddress(final byte[] bytes) {
        return ContractID.newBuilder()
                .setShardNum(Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4)))
                .setRealmNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 4, 12)))
                .setContractNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20)))
                .build();
    }

    public static TokenID tokenIdFromEvmAddress(final byte[] bytes) {
        return TokenID.newBuilder()
                .setShardNum(Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4)))
                .setRealmNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 4, 12)))
                .setTokenNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20)))
                .build();
    }

    public static Address asTypedEvmAddress(final ContractID id) {
        return Address.wrap(Bytes.wrap(asEvmAddress(id)));
    }

    public static Address asTypedEvmAddress(final AccountID id) {
        return Address.wrap(Bytes.wrap(asEvmAddress(id)));
    }

    public static Address asTypedEvmAddress(final TokenID id) {
        return Address.wrap(Bytes.wrap(asEvmAddress(id)));
    }

    public static ContractID contractIdFromEvmAddress(final Address address) {
        return contractIdFromEvmAddress(address.toArrayUnsafe());
    }

    public static TokenID tokenIdFromEvmAddress(final Address address) {
        return tokenIdFromEvmAddress(address.toArrayUnsafe());
    }

    public static long[] asDotDelimitedLongArray(String s) {
        String[] parts = s.split("[.]");
        return Stream.of(parts).mapToLong(Long::valueOf).toArray();
    }

    public static AccountID parseAccount(final String literal) {
        try {
            final var parts = parseLongTriple(literal);
            return AccountID.newBuilder()
                    .setShardNum(parts[0])
                    .setRealmNum(parts[1])
                    .setAccountNum(parts[2])
                    .build();
        } catch (final NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(String.format("Argument 'literal=%s' is not an account", literal), e);
        }
    }

    public static AccountID toGrpcAccountId(final int code) {
        return AccountID.newBuilder()
                .setShardNum(0L)
                .setRealmNum(0L)
                .setAccountNum(numFromCode(code))
                .build();
    }

    private static long[] parseLongTriple(final String dotDelimited) {
        final long[] triple = new long[3];
        int i = 0;
        long v = 0;
        for (final char c : dotDelimited.toCharArray()) {
            if (c == '.') {
                triple[i++] = v;
                v = 0;
            } else if (c < '0' || c > '9') {
                throw new NumberFormatException(CANNOT_PARSE_PREFIX + dotDelimited + "' due to character '" + c + "'");
            } else {
                v = 10 * v + (c - '0');
                if (v < 0) {
                    throw new IllegalArgumentException(CANNOT_PARSE_PREFIX + dotDelimited + "' due to overflow");
                }
            }
        }
        if (i < 2) {
            throw new IllegalArgumentException(CANNOT_PARSE_PREFIX + dotDelimited + "' due to only " + i + " dots");
        }
        triple[i] = v;
        return triple;
    }

    public static String asHexedEvmAddress(final Id id) {
        return CommonUtils.hex(asEvmAddress((int) id.shard(), id.realm(), id.num()));
    }

    public static boolean isAlias(final AccountID idOrAlias) {
        return idOrAlias.getAccountNum() == 0 && !idOrAlias.getAlias().isEmpty();
    }

    public static EntityId entityIdFromId(Id id) {
        if (id == null) {
            return null;
        }
        return new EntityId(id.shard(), id.realm(), id.num(), EntityType.UNKNOWN);
    }

    public static Id idFromEntityId(EntityId entityId) {
        if (entityId == null) {
            return null;
        }
        return new Id(entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum());
    }
}
