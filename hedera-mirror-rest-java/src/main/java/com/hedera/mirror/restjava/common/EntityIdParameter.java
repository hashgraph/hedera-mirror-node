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

package com.hedera.mirror.restjava.common;

import static com.hedera.mirror.restjava.common.Constants.BASE32;
import static com.hedera.mirror.restjava.common.Constants.HEX_PREFIX;

import com.hedera.mirror.common.domain.entity.EntityId;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.jooq.tools.StringUtils;

public record EntityIdParameter(
        EntityId num, byte[] evmAddress, byte[] alias, Long shard, Long realm, EntityIdType type) {
    public static final EntityIdParameter EMPTY = new EntityIdParameter(null, null, null, 0L, 0L, null);

    public static final String ACCOUNT_ALIAS_REGEX = "^((\\d{1,5})\\.)?((\\d{1,5})\\.)?([A-Z2-7]+)$";

    public static final String ENTITY_ID_REGEX = "^((\\d{1,5})\\.)?((\\d{1,5})\\.)?(\\d{1,10})$";
    public static final String EVM_ADDRESS_REGEX = "^(((0x)?|((\\d{1,5})\\.)?((\\d{1,5})\\.)?)([A-Fa-f0-9]{40}))$";
    public static final int EVM_ADDRESS_MIN_LENGTH = 40;

    public static final Pattern ENTITY_ID_PATTERN = Pattern.compile(ENTITY_ID_REGEX);
    public static final Pattern EVM_ADDRESS_PATTERN = Pattern.compile(EVM_ADDRESS_REGEX);
    public static final Pattern ALIAS_PATTERN = Pattern.compile(ACCOUNT_ALIAS_REGEX);

    public static EntityIdParameter valueOf(String entityIdParam) {

        if (StringUtils.isBlank(entityIdParam)) {
            return EMPTY;
        }

        return parseId(entityIdParam);
    }

    public static EntityIdParameter parseId(String id) {

        if (org.apache.commons.lang3.StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException(" Id '%s' has an invalid format".formatted(id));
        }

        EntityIdParameter entityId;
        if ((entityId = parseEntityId(id)) != null) {
            return entityId;
        } else if ((entityId = parseEvmAddress(id)) != null) {
            return entityId;
        } else if ((entityId = parseAlias(id)) != null) {
            return entityId;
        } else {
            throw new IllegalArgumentException("Id %s format is invalid".formatted(id));
        }
    }

    private static EntityIdParameter parseEvmAddress(String id) {
        var evmMatcher = EVM_ADDRESS_PATTERN.matcher(id);
        Long shard = null;
        Long realm = 0L;

        if (!evmMatcher.matches()) {
            return null;
        }
        if (evmMatcher.group(6) != null) {
            // This gets the realm value
            realm = Long.parseLong(evmMatcher.group(7));
            shard = Long.parseLong(evmMatcher.group(5));

        } else if (evmMatcher.group(4) != null) {
            realm = Long.parseLong(evmMatcher.group(5));
        }

        return new EntityIdParameter(
                null, decodeEvmAddress(evmMatcher.group(8)), null, shard, realm, EntityIdType.EVMADDRESS);
    }

    private static EntityIdParameter parseAlias(String id) {
        var aliasMatcher = ALIAS_PATTERN.matcher(id);
        Long shard = null;
        Long realm = 0L;

        if (!aliasMatcher.matches()) {
            return null;
        }
        if (aliasMatcher.group(3) != null) {
            // This gets the shard and realm value
            realm = Long.parseLong(aliasMatcher.group(4));
            shard = Long.parseLong(aliasMatcher.group(2));

        } else if (aliasMatcher.group(1) != null) {
            // This gets the realm value and shard will be null
            realm = Long.parseLong(aliasMatcher.group(2));
        }

        return new EntityIdParameter(null, null, decodeBase32(aliasMatcher.group(5)), shard, realm, EntityIdType.ALIAS);
    }

    private static EntityIdParameter parseEntityId(String id) {

        var matcher = ENTITY_ID_PATTERN.matcher(id);
        Long shard = null;
        Long realm = 0L;

        if (!matcher.matches()) {
            return null;
        }

        if (matcher.group(3) != null) {
            // This gets the shard and realm value
            realm = Long.parseLong(matcher.group(4));
            shard = Long.parseLong(matcher.group(2));

        } else if (matcher.group(1) != null) {
            // This gets the realm value and shard will be null
            realm = Long.parseLong(matcher.group(2));
        }
        var num = matcher.group(5);

        return new EntityIdParameter(
                EntityId.of(shard == null ? 0 : shard, realm, Long.parseLong(num)),
                null,
                null,
                shard,
                realm,
                EntityIdType.NUM);
    }

    public static byte[] decodeBase32(String base32) {
        return BASE32.decode(base32);
    }

    public static byte[] decodeEvmAddress(String evmAddress) {
        if (evmAddress == null) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        try {
            evmAddress = org.apache.commons.lang3.StringUtils.removeStart(evmAddress, HEX_PREFIX);
            return Hex.decodeHex(evmAddress);
        } catch (DecoderException e) {
            throw new IllegalArgumentException("Unable to decode evmAddress: " + evmAddress);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityIdParameter that = (EntityIdParameter) o;
        return Objects.equals(num, that.num)
                && Arrays.equals(evmAddress, that.evmAddress)
                && Arrays.equals(alias, that.alias)
                && type == that.type;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(num, type);
        result = 31 * result + Arrays.hashCode(evmAddress);
        result = 31 * result + Arrays.hashCode(alias);
        return result;
    }

    @Override
    public String toString() {
        return "EntityIdParameter{" + "num="
                + num + ", evmAddress="
                + Arrays.toString(evmAddress) + ", alias="
                + Arrays.toString(alias) + ", type="
                + type + '}';
    }
}
