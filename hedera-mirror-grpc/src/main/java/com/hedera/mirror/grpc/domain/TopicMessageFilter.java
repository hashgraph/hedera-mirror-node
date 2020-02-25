package com.hedera.mirror.grpc.domain;

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

import java.time.Instant;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.grpc.validation.EndTime;
import com.hedera.mirror.grpc.validation.StartTime;

@Builder(toBuilder = true)
@EndTime
@StartTime
@Validated
@Value
public class TopicMessageFilter {

    private Instant endTime;

    @Min(0)
    private long limit;

    @Min(0)
    private int realmNum;

    @NotNull
    @Builder.Default
    private Instant startTime = Instant.now();

    @Builder.Default
    private String subscriberId = RandomStringUtils.randomAlphanumeric(8);

    @Min(0)
    private int topicNum;

    public boolean hasLimit() {
        return limit > 0;
    }
}
