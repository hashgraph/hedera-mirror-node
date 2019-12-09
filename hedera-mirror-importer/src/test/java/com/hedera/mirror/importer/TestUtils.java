package com.hedera.mirror.importer;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;

import com.hedera.mirror.importer.util.Utility;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;

public final class TestUtils {
    /**
     * Convert a string input parameter to a byte array. String values of "null" result in null result, "empty" result
     * in empty byte array return.
     *
     * @param val
     * @return
     */
    public static byte[] toByteArray(String val) {
        if (null == val || val.equalsIgnoreCase("null")) {
            return null;
        } else if (val.equalsIgnoreCase("empty")) {
            return new byte[0];
        }
        return val.getBytes();
    }

    /**
     * Convert a string input parameter to a string where values of "null" result in null result, "empty" result in
     * empty string returned.
     *
     * @param val
     * @return
     */
    public static String toStringWithNullOrEmpty(String val) {
        if (null == val || val.equalsIgnoreCase("null")) {
            return null;
        } else if (val.equalsIgnoreCase("empty")) {
            return new String();
        }
        return val;
    }

    public static AccountID toAccountId(String accountId) {
        var parts = accountId.split("\\.");
        return AccountID.newBuilder().setShardNum(Long.parseLong(parts[0])).setRealmNum(Long.parseLong(parts[1]))
                .setAccountNum(Long.parseLong(parts[2])).build();
    }

    public static TransactionID toTransactionId(String transactionId) {
        var parts = transactionId.split("-");
        return TransactionID.newBuilder().setAccountID(toAccountId(parts[0]))
                .setTransactionValidStart(toTimestamp(Long.valueOf(parts[1]))).build();
    }

    public static TopicID toTopicId(String topicId) {
        var parts = topicId.split("\\.");
        return TopicID.newBuilder().setShardNum(Long.parseLong(parts[0])).setRealmNum(Long.parseLong(parts[1]))
                .setTopicNum(Long.parseLong(parts[2])).build();
    }

    public static Key toKey(String key) {
        var bytes = toByteArray(key);
        if (null == bytes) {
            return null;
        } else if (0 == bytes.length) {
            return Key.newBuilder().build();
        } else {
            return Key.newBuilder().setEd25519(ByteString.copyFrom(bytes)).build();
        }
    }

    public static Timestamp toTimestamp(long nanosecondsSinceEpoch) {
        return Utility.instantToTimestamp(Instant.ofEpochSecond(0, nanosecondsSinceEpoch));
    }

    public static Timestamp toTimestamp(long seconds, long nanoseconds) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos((int) nanoseconds).build();
    }
}
