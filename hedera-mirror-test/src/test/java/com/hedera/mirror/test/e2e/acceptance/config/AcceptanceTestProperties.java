/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.config;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.props.NodeProperties;
import jakarta.inject.Named;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Named
@ConfigurationProperties(prefix = "hedera.mirror.test.acceptance")
@Data
@RequiredArgsConstructor
@Validated
public class AcceptanceTestProperties {

    private final FeatureProperties featureProperties;
    private final RestPollingProperties restPollingProperties;
    private final WebClientProperties webClientProperties;

    @NotNull
    private Duration backOffPeriod = Duration.ofMillis(5000);

    // A new account is usually necessary since shared accounts like 0.0.2 might reach maxTokensPerAccount, etc
    private boolean createOperatorAccount = true;

    private boolean emitBackgroundMessages = false;

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

    @Max(50_000_000_000L * 100_000_000L)
    @Min(100_000_000L)
    private long operatorBalance = Hbar.from(800).toTinybars();

    @NotBlank
    private String operatorId = "0.0.2";

    @NotBlank
    private String operatorKey =
            "302e020100300506032b65700422042091132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137";

    private boolean retrieveAddressBook = true;

    @DurationMin(seconds = 0L)
    @NotNull
    private Duration startupTimeout = Duration.ofMinutes(60);

    public enum HederaNetwork {
        MAINNET,
        OTHER,
        PREVIEWNET,
        TESTNET,
    }
}
