package com.hedera.mirror.test.e2e.acceptance.config;

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

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Named;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.props.NodeProperties;

@Named
@ConfigurationProperties(prefix = "hedera.mirror.test.acceptance")
@Data
@Validated
public class AcceptanceTestProperties {

    private final FeatureProperties featureProperties;
    private final RestPollingProperties restPollingProperties;
    private final SdkProperties sdkProperties;
    private final WebClientProperties webClientProperties;

    @NotNull
    private Duration backOffPeriod = Duration.ofMillis(5000);

    private boolean createOperatorAccount = false;

    private boolean emitBackgroundMessages = false;
    private boolean contractTraceability = false;

    @Min(1)
    private int maxNodes = 10;

    @Max(5)
    private int maxRetries = 2;

    @Min(1L)
    private long maxTinyBarTransactionFee = Hbar.from(50).toTinybars();

    @DurationMin(seconds = 1)
    @NotNull
    private Duration messageTimeout = Duration.ofSeconds(20);

    @NotBlank
    private String mirrorNodeAddress;

    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    @NotNull
    private Set<NodeProperties> nodes = new LinkedHashSet<>();

    @Min(100_000_000L)
    private long operatorBalance = Hbar.from(260).toTinybars();

    @NotBlank
    private String operatorId = "0.0.2";

    @NotBlank
    private String operatorKey =
            "302e020100300506032b65700422042091132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137";

    private boolean retrieveAddressBook = true;

    @DurationMin(seconds = 0L)
    @NotNull
    private Duration startupTimeout = Duration.ofMinutes(30);

    public enum HederaNetwork {
        MAINNET,
        OTHER,
        PREVIEWNET,
        TESTNET,
    }
}
