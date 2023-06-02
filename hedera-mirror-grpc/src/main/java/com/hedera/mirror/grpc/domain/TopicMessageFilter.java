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

package com.hedera.mirror.grpc.domain;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.grpc.converter.InstantToLongConverter;
import com.hedera.mirror.grpc.validation.EndTime;
import com.hedera.mirror.grpc.validation.StartTime;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.validation.annotation.Validated;

@Builder(toBuilder = true)
@EndTime
@StartTime
@Validated
@Value
public class TopicMessageFilter {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Instant endTime;

    @Min(0)
    private long limit;

    @NotNull
    @Builder.Default
    private Instant startTime = Instant.now();

    @Getter(lazy = true)
    @ToString.Exclude
    private Long startTimeLong = InstantToLongConverter.INSTANCE.convert(startTime);

    @Builder.Default
    private String subscriberId = RandomStringUtils.random(8, 0, 0, true, true, null, RANDOM);

    @NotNull
    private EntityId topicId;

    public boolean hasLimit() {
        return limit > 0;
    }
}
