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

package com.hedera.mirror.monitor.subscribe.grpc;

import com.hedera.mirror.monitor.subscribe.AbstractSubscriberProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class GrpcSubscriberProperties extends AbstractSubscriberProperties {

    @NotNull
    private Instant startTime = Instant.now();

    @NotBlank
    private String topicId;

    public GrpcSubscriberProperties() {
        retry.setMaxAttempts(Long.MAX_VALUE); // gRPC subscription only occurs once so retry indefinitely
        retry.setMaxBackoff(Duration.ofSeconds(8L));
    }

    public Instant getEndTime() {
        return startTime.plus(duration);
    }
}
