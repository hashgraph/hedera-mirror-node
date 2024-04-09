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

import com.google.common.io.BaseEncoding;

import java.util.regex.Pattern;

public record EntityIdAliasParameter(long shard, long realm, byte[] alias) implements EntityIdParameter {

    public static final String ACCOUNT_ALIAS_REGEX = "^((\\d{1,5})\\.)?((\\d{1,5})\\.)?([A-Z2-7]+)$";

    public static final Pattern ALIAS_PATTERN = Pattern.compile(ACCOUNT_ALIAS_REGEX);
    private static final BaseEncoding BASE32 = BaseEncoding.base32().omitPadding();

    public static EntityIdAliasParameter valueOf(String id) {
        var aliasMatcher = ALIAS_PATTERN.matcher(id);

        if (!aliasMatcher.matches()) {
            return null;
        }

        long shard = 0;
        long realm = 0;

        if (aliasMatcher.group(3) != null) {
            // This gets the shard and realm value
            realm = Long.parseLong(aliasMatcher.group(4));
            shard = Long.parseLong(aliasMatcher.group(2));

        } else if (aliasMatcher.group(1) != null) {
            // This gets the realm value and shard will be null
            realm = Long.parseLong(aliasMatcher.group(2));
        }

        return new EntityIdAliasParameter(shard, realm, BASE32.decode(aliasMatcher.group(5)));
    }
}
