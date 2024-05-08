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

package com.hedera.mirror.web3.evm.utils;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TransactionUtils {

    private static final Pattern ETH_HASH_PATTERN = Pattern.compile("^(0x)?([0-9A-Fa-f]{64})$");
    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)-(\\d{1,19})-(\\d{1,9})$");

    public static boolean isValidEthHash(String hash) {
        if (hash == null) {
            return false;
        }

        Matcher matcher = ETH_HASH_PATTERN.matcher(hash);
        return matcher.matches();
    }

    public static boolean isValidTransactionId(String transactionId) {
        if (transactionId == null) {
            return false;
        }

        Matcher matcher = TRANSACTION_ID_PATTERN.matcher(transactionId);
        return matcher.matches();
    }

    public static TransactionID parseTransactionId(String transactionId) {
        Matcher matcher = TRANSACTION_ID_PATTERN.matcher(transactionId);

        if (!matcher.matches() || matcher.groupCount() != 5) {
            throw new IllegalArgumentException("Invalid Transaction ID. " +
                    "Please use \"shard.realm.num-sss-nnn\" format where sss are seconds and nnn are nanoseconds");
        }

        long shard = Long.parseLong(matcher.group(1));
        long realm = Long.parseLong(matcher.group(2));
        long num = Long.parseLong(matcher.group(3));
        long seconds = Long.parseLong(matcher.group(4));
        int nanos = Integer.parseInt(matcher.group(5));

        if (seconds < 0) {
            throw new IllegalArgumentException("Seconds cannot be less than 0");
        }

        return TransactionID.newBuilder()
                .setAccountID(AccountID.newBuilder()
                        .setShardNum(shard)
                        .setRealmNum(realm)
                        .setAccountNum(num)
                        .build())
                .setTransactionValidStart(Timestamp.newBuilder()
                        .setSeconds(seconds)
                        .setNanos(nanos)
                        .build())
                .build();
    }
}
