/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

@SuppressWarnings("java:S6218")
public record EntityIdEvmAddressParameter(long shard, long realm, byte[] evmAddress) implements EntityIdParameter {

    public static final String EVM_ADDRESS_REGEX = "^((\\d{1,5})\\.)?((\\d{1,5})\\.)?(0x)?([A-Fa-f0-9]{40})$";
    public static final Pattern EVM_ADDRESS_PATTERN = Pattern.compile(EVM_ADDRESS_REGEX);

    @SneakyThrows(DecoderException.class)
    public static EntityIdEvmAddressParameter valueOf(String id) {
        var evmMatcher = EVM_ADDRESS_PATTERN.matcher(id);

        if (!evmMatcher.matches()) {
            return null;
        }

        long shard = DEFAULT_SHARD;
        long realm = 0;
        String realmString;

        if ((realmString = evmMatcher.group(4)) != null) {
            realm = Long.parseLong(realmString);
            shard = Long.parseLong(evmMatcher.group(2));
        } else if ((realmString = evmMatcher.group(2)) != null) {
            realm = Long.parseLong(realmString);
        }

        var evmAddress = Hex.decodeHex(evmMatcher.group(6));
        return new EntityIdEvmAddressParameter(shard, realm, evmAddress);
    }
}
