package com.hedera.mirror.test.e2e.acceptance.config;

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

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.test.e2e.acceptance.props.NodeProperties;

@Component
@ConfigurationProperties(prefix = "hedera.mirror.test.acceptance")
@Data
@Validated
public class AcceptanceTestProperties {
    private final RestPollingProperties restPollingProperties;

    @NotNull
    private Set<NodeProperties> nodes = new LinkedHashSet<>();
    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;
    @NotBlank
    private String mirrorNodeAddress;
    @NotBlank
    private String operatorId;
    @NotBlank
    private String operatorKey;
    @NotNull
    private Duration messageTimeout = Duration.ofSeconds(20);
    @NotNull
    private Long existingTopicNum;

    private boolean emitBackgroundMessages = false;

    @Max(5)
    private int subscribeRetries = 5;

    @NotNull
    private Duration subscribeRetryBackoffPeriod = Duration.ofMillis(5000);

    @NotNull
    private Long maxTinyBarTransactionFee = 1_000_000_000L;

    public Set<NodeProperties> getNodes() {
        if (network == HederaNetwork.OTHER && nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be empty");
        }
        return nodes;
    }

    public enum HederaNetwork {
        MAINNET,
        PREVIEWNET,
        TESTNET,
        OTHER
    }
}
