/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.service.evm.store.models.HederaEvmAccount.ECDSA_SECP256K1_ALIAS_SIZE;
import static com.hedera.services.utils.BitPackUtils.numFromCode;
import static java.lang.System.arraycopy;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public final class EntityIdUtils {
    private static final String CANNOT_PARSE_PREFIX = "Cannot parse '";
    private static final String ENTITY_ID_FORMAT = "%d.%d.%d";

    private EntityIdUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static byte[] asEvmAddress(final ContractID id) {
        if (isOfEvmAddressSize(id.getEvmAddress())) {
            return id.getEvmAddress().toByteArray();
        } else {
            return asEvmAddress(id.getContractNum());
        }
    }

    public static byte[] asEvmAddress(final AccountID id) {
        return asEvmAddress(id.getAccountNum());
    }

    public static byte[] asEvmAddress(final TokenID id) {
        return asEvmAddress(id.getTokenNum());
    }

    public static byte[] asEvmAddress(final long num) {
        final byte[] evmAddress = new byte[20];
        arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);
        return evmAddress;
    }

    public static long numFromEvmAddress(final byte[] bytes) {
        return Longs.fromBytes(bytes[12], bytes[13], bytes[14], bytes[15], bytes[16], bytes[17], bytes[18], bytes[19]);
    }

    public static AccountID accountIdFromEvmAddress(final Address address) {
        return accountIdFromEvmAddress(address.toArrayUnsafe());
    }

    public static AccountID accountIdFromEvmAddress(final byte[] bytes) {
        return AccountID.newBuilder().setAccountNum(numFromEvmAddress(bytes)).build();
    }

    public static ContractID contractIdFromEvmAddress(final byte[] bytes) {
        return ContractID.newBuilder()
                .setContractNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20)))
                .build();
    }

    public static TokenID tokenIdFromEvmAddress(final byte[] bytes) {
        return TokenID.newBuilder()
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

    public static boolean isOfEvmAddressSize(final ByteString alias) {
        return alias.size() == EVM_ADDRESS_SIZE;
    }

    public static boolean isOfEcdsaPublicAddressSize(final ByteString alias) {
        return alias.size() == ECDSA_SECP256K1_ALIAS_SIZE;
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

    public static AccountID toGrpcAccountId(final Id id) {
        return AccountID.newBuilder()
                .setShardNum(id.shard())
                .setRealmNum(id.realm())
                .setAccountNum(id.num())
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

    public static String asHexedEvmAddress(final AccountID id) {
        return CommonUtils.hex(asEvmAddress(id.getAccountNum()));
    }

    public static String asHexedEvmAddress(final Id id) {
        return CommonUtils.hex(asEvmAddress(id.num()));
    }

    public static boolean isAlias(final AccountID idOrAlias) {
        return idOrAlias.getAccountNum() == 0 && !idOrAlias.getAlias().isEmpty();
    }

    public static EntityId entityIdFromId(Id id) {
        if (id == null) {
            return null;
        }
        return EntityId.of(id.shard(), id.realm(), id.num());
    }

    public static EntityId entityIdFromNftId(NftId id) {
        if (id == null) {
            return null;
        }
        return EntityId.of(id.shard(), id.realm(), id.num());
    }

    public static Id idFromEntityId(EntityId entityId) {
        if (entityId == null) {
            return null;
        }
        return new Id(entityId.getShard(), entityId.getRealm(), entityId.getNum());
    }

    public static String readableId(final Object o) {
        if (o instanceof Id id) {
            return String.format(ENTITY_ID_FORMAT, id.shard(), id.realm(), id.num());
        }
        if (o instanceof AccountID id) {
            return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getAccountNum());
        }
        if (o instanceof TokenID id) {
            return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getTokenNum());
        }
        if (o instanceof NftID id) {
            final var tokenID = id.getTokenID();
            return String.format(
                    ENTITY_ID_FORMAT + ".%d",
                    tokenID.getShardNum(),
                    tokenID.getRealmNum(),
                    tokenID.getTokenNum(),
                    id.getSerialNumber());
        }
        return String.valueOf(o);
    }

    public static boolean isAliasSizeGreaterThanEvmAddress(final ByteString alias) {
        return alias.size() > EVM_ADDRESS_SIZE;
    }
}
