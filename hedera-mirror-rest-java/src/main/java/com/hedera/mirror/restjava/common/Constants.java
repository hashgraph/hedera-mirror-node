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

import com.google.common.base.Splitter;

public class Constants {

    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 25;

    public static final int MIN_LIMIT = 1;

    // format: |0|15-bit shard|16-bit realm|32-bit num|
    public static final long numBits = 32;
    public static final long maxNum = (long) Math.pow(2, numBits) - 1;

    public static final long realmBits = 16;
    public static final long maxRealm = (long) Math.pow(2, realmBits) - 1;
    public static final long shardBits = 15;

    public static final long maxShard = (long) Math.pow(2, shardBits) - 1;
    public static final long maxSafeShard = (long) Math.pow(2, 5) - 1;

    public static final long maxEncodedId = (long) Math.pow(2, 63) - 1;

    public static final String entityIdRegex = "^(\\d{1,5}\\.){1,2}\\d{1,10}$";
    public static final String encodedEntityIdRegex = "^\\d{1,19}$";

    public static final String evmAddressShardRealmRegex = "^(\\d{1,10}\\.){0,2}[A-Fa-f0-9]{40}$";
    public static final String evmAddressRegex = "^(0x)?[A-Fa-f0-9]{40}$";

    public static final Splitter SPLITTER = Splitter.on('.').omitEmptyStrings().trimResults();

    public static final String ACCOUNT_ID = "account.id";
    public static final String TOKEN_ID = "token.id";

    public static final String ORDER = "order";
    public static final String OWNER = "owner";
    public static final String LIMIT = "limit";

    enum EvmAddressType {
        // evm address without shard and realm and with 0x prefix
        NO_SHARD_REALM,
        // evm address with shard and realm as optionals
        OPTIONAL_SHARD_REALM,
        // can be either a NO_SHARD_REALM or OPTIONAL_SHARD_REALM
        ANY,
    }
}
