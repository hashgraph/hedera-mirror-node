package com.hedera.mirror.grpc.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.grpc.util.ProtoUtil.DB_ERROR;
import static com.hedera.mirror.grpc.util.ProtoUtil.OVERFLOW_ERROR;
import static com.hedera.mirror.grpc.util.ProtoUtil.UNKNOWN_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import javax.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.QueryTimeoutException;
import reactor.core.Exceptions;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.grpc.exception.EntityNotFoundException;

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

    @Test
    void toStatusRuntimeException() {
        var entityId = EntityId.of(1L, EntityType.ACCOUNT);
        var message = "boom";

        assertException(Exceptions.failWithOverflow(message), Status.DEADLINE_EXCEEDED, OVERFLOW_ERROR);
        assertException(new ConstraintViolationException(message, null), Status.INVALID_ARGUMENT, message);
        assertException(new IllegalArgumentException(message), Status.INVALID_ARGUMENT, message);
        assertException(new InvalidEntityException(message), Status.INVALID_ARGUMENT, message);
        assertException(new EntityNotFoundException(entityId), Status.NOT_FOUND, "Account 0.0.1 does not exist");
        assertException(new NonTransientDataAccessResourceException(message), Status.UNAVAILABLE, DB_ERROR);
        assertException(new QueryTimeoutException(message), Status.RESOURCE_EXHAUSTED, DB_ERROR);
        assertException(new TimeoutException(message), Status.RESOURCE_EXHAUSTED, DB_ERROR);
        assertException(new RuntimeException(message), Status.UNKNOWN, UNKNOWN_ERROR);
    }

    void assertException(Throwable t, Status status, String message) {
        assertThat(ProtoUtil.toStatusRuntimeException(t))
                .isNotNull()
                .hasMessageContaining(message)
                .extracting(StatusRuntimeException::getStatus)
                .extracting(Status::getCode)
                .isEqualTo(status.getCode());
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
