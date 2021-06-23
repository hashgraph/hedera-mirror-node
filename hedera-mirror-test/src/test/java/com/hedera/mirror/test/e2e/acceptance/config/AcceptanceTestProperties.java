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
import javax.inject.Named;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.test.e2e.acceptance.props.NodeProperties;

@Named
@ConfigurationProperties(prefix = "hedera.mirror.test.acceptance")
@Data
@Validated
public class AcceptanceTestProperties {
    @NotNull
    private Duration backOffPeriod = Duration.ofMillis(5000);

    private boolean emitBackgroundMessages = false;

    @NotNull
    private Long existingTopicNum;

    @Max(10)
    private int maxRetries = 3;

    @NotNull
    private Long maxTinyBarTransactionFee = 1_000_000_000L;

    @NotNull
    private Duration messageTimeout = Duration.ofSeconds(60);

    @NotBlank
    private String mirrorNodeAddress;

    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    private Set<NodeProperties> nodes = new LinkedHashSet<>();

    @NotBlank
    private String operatorId;

    @NotBlank
    private String operatorKey;

    private final RestPollingProperties restPollingProperties;

    private boolean retrieveAddressBook = true;

    private final SdkProperties sdkProperties;

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
