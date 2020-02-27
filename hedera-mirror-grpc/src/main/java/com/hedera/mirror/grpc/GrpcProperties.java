package com.hedera.mirror.grpc;

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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.grpc.config.NettyProperties;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.grpc")
public class GrpcProperties {

    private boolean checkTopicExists = true;

    @NotNull
    private Map<String, String> connectionOptions = new HashMap<>();

    @NotNull
    private Duration endTimeInterval = Duration.ofSeconds(30);

    @NotNull
    private NettyProperties netty = new NettyProperties();

    private long shard = 0;
}
