package com.hedera.mirror.grpc.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import com.google.protobuf.UnsafeByteOperations;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import lombok.experimental.UtilityClass;

import com.hedera.mirror.common.domain.entity.EntityId;

@UtilityClass
public final class ProtoUtil {
    public static Instant fromTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }

        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static AccountID toAccountID(EntityId entityId) {
        return AccountID.newBuilder()
                .setShardNum(entityId.getShardNum())
                .setRealmNum(entityId.getRealmNum())
                .setAccountNum(entityId.getEntityNum())
                .build();
    }

    public static ByteString toByteString(byte[] bytes) {
        if (bytes == null) {
            return ByteString.EMPTY;
        }
        return UnsafeByteOperations.unsafeWrap(bytes);
    }

    public static Timestamp toTimestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp
                .newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
