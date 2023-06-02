/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.grpc.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.grpc.exception.EntityNotFoundException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.TransientDataAccessException;
import reactor.core.Exceptions;

@Log4j2
@UtilityClass
public final class ProtoUtil {

    static final String DB_ERROR = "Error querying the data source. Please retry later";
    static final String OVERFLOW_ERROR = "Client lags too much behind. Please retry later";
    static final String UNKNOWN_ERROR = "Unknown error";

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

    public static StatusRuntimeException toStatusRuntimeException(Throwable t) {
        if (Exceptions.isOverflow(t)) {
            return clientError(t, Status.DEADLINE_EXCEEDED, OVERFLOW_ERROR);
        } else if (t instanceof ConstraintViolationException
                || t instanceof IllegalArgumentException
                || t instanceof InvalidEntityException) {
            return clientError(t, Status.INVALID_ARGUMENT, t.getMessage());
        } else if (t instanceof EntityNotFoundException) {
            return clientError(t, Status.NOT_FOUND, t.getMessage());
        } else if (t instanceof TransientDataAccessException || t instanceof TimeoutException) {
            return serverError(t, Status.RESOURCE_EXHAUSTED, DB_ERROR);
        } else if (t instanceof NonTransientDataAccessResourceException) {
            return serverError(t, Status.UNAVAILABLE, DB_ERROR);
        } else {
            return serverError(t, Status.UNKNOWN, UNKNOWN_ERROR);
        }
    }

    private static StatusRuntimeException clientError(Throwable t, Status status, String message) {
        log.warn("Client error {}: {}", t.getClass().getSimpleName(), t.getMessage());
        return status.augmentDescription(message).asRuntimeException();
    }

    private static StatusRuntimeException serverError(Throwable t, Status status, String message) {
        log.error("Server error: ", t);
        return status.augmentDescription(message).asRuntimeException();
    }

    public static Timestamp toTimestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
