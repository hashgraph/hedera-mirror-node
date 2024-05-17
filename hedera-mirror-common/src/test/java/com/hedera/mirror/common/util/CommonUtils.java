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

package com.hedera.mirror.common.util;

import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.security.SecureRandom;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommonUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static byte[] nextBytes(int length) {
        var bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static AccountID toAccountID(EntityId entityId) {
        return AccountID.newBuilder()
                .setShardNum(entityId.getShard())
                .setRealmNum(entityId.getRealm())
                .setAccountNum(entityId.getNum())
                .build();
    }

    public static ContractID toContractID(EntityId entityId) {
        return ContractID.newBuilder()
                .setShardNum(entityId.getShard())
                .setRealmNum(entityId.getRealm())
                .setContractNum(entityId.getNum())
                .setEvmAddress(ByteString.copyFrom(toEvmAddress(entityId)))
                .build();
    }

    public static Duration duration(int seconds) {
        return Duration.newBuilder().setSeconds(seconds).build();
    }

    public static Timestamp timestamp(long nanos) {
        final long seconds = nanos / 1_000_000_000;
        final int remainingNanos = (int) (nanos % 1_000_000_000);
        return timestamp(seconds, remainingNanos);
    }

    public static Timestamp timestamp(long seconds, int nanos) {
        return Timestamp.newBuilder()
                .setSeconds(seconds)
                .setNanos(nanos)
                .build();
    }
}
