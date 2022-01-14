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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;

class ProtoUtilTest {

    @DisplayName("Convert Timestamp to Instant")
    @ParameterizedTest(name = "with {0}s and {1}ns")
    @CsvSource({
            "0, 0",
            "0, 999999999",
            "10, 0",
            "31556889864403199, 999999999",
            "-31557014167219200, 0"
    })
    void fromTimestamp(long seconds, int nanos) {
        Instant instant = Instant.ofEpochSecond(seconds, nanos);
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        assertThat(ProtoUtil.fromTimestamp(timestamp)).isEqualTo(instant);
    }

    @DisplayName("Convert Instant to Timestamp")
    @ParameterizedTest(name = "with {0}s and {1}ns")
    @CsvSource({
            "0, 0",
            "0, 999999999",
            "10, 0",
            "31556889864403199, 999999999",
            "-31557014167219200, 0"
    })
    void toTimestamp(long seconds, int nanos) {
        Instant instant = Instant.ofEpochSecond(seconds, nanos);
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        assertThat(ProtoUtil.toTimestamp(instant)).isEqualTo(timestamp);
    }

    @Test
    void toAccountID() {
        assertThat(ProtoUtil.toAccountID(EntityId.of(0L, 0L, 5L, EntityType.ACCOUNT)))
                .returns(0L, AccountID::getShardNum)
                .returns(0L, AccountID::getRealmNum)
                .returns(5L, AccountID::getAccountNum);
        assertThat(ProtoUtil.toAccountID(EntityId.of(1L, 2L, 3L, EntityType.ACCOUNT)))
                .returns(1L, AccountID::getShardNum)
                .returns(2L, AccountID::getRealmNum)
                .returns(3L, AccountID::getAccountNum);
    }

    @Test
    void toByteString() {
        var bytes = new byte[] {0, 1, 2, 3};
        assertThat(ProtoUtil.toByteString(null)).isEqualTo(ByteString.EMPTY);
        assertThat(ProtoUtil.toByteString(new byte[] {})).isEqualTo(ByteString.EMPTY);
        assertThat(ProtoUtil.toByteString(bytes)).isEqualTo(ByteString.copyFrom(bytes))
                .isNotSameAs(ProtoUtil.toByteString(bytes));
    }
}
