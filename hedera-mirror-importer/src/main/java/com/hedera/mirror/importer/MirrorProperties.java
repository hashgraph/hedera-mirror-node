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

package com.hedera.mirror.importer;

import com.hedera.mirror.importer.migration.MigrationProperties;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.mirror.importer.validation.Network;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer")
public class MirrorProperties {
    static final String NETWORK_PREFIX_DELIMITER = "-";

    @NotNull
    private ConsensusMode consensusMode = ConsensusMode.STAKE_IN_ADDRESS_BOOK;

    @NotNull
    private Path dataPath = Paths.get(".", "data");

    @NotNull
    private Instant endDate = Utility.MAX_INSTANT_LONG;

    private boolean importHistoricalAccountInfo = true;

    private Path initialAddressBook;

    @NotNull
    private Map<String, MigrationProperties> migration = new CaseInsensitiveMap<>();

    @Network
    private String network = HederaNetwork.DEMO.name().toLowerCase();

    @Min(0)
    private long shard = 0L;

    private Instant startDate;

    private Long startBlockNumber;

    private Long topicRunningHashV2AddedTimestamp;

    @NotNull
    private Instant verifyHashAfter = Instant.EPOCH;

    public void setNetwork(@NonNull String network) {
        HederaNetwork.getHederaNetworkByName(network);
        this.network = network.toLowerCase();
    }

    public HederaNetwork getHederaNetwork() {
        return HederaNetwork.getHederaNetworkByName(this.network);
    }

    public Optional<String> getNetworkPrefix() {
        var networkAndPrefix = this.network;
        var delimiterIndex = networkAndPrefix.indexOf(NETWORK_PREFIX_DELIMITER);
        return delimiterIndex < 0 || delimiterIndex == networkAndPrefix.length() - 1
                ? Optional.empty()
                : Optional.of(networkAndPrefix.substring(delimiterIndex + 1));
    }

    public enum ConsensusMode {
        EQUAL, // all nodes equally weighted
        STAKE, // all nodes specify their node stake
        STAKE_IN_ADDRESS_BOOK // like STAKE, but only the nodes found in the address book are used in the calculation.
    }

    @Getter
    @RequiredArgsConstructor
    public enum HederaNetwork {
        DEMO("hedera-demo-streams"),
        MAINNET("hedera-mainnet-streams"),
        PREVIEWNET("hedera-preview-testnet-streams"),
        OTHER(""), // Pre-prod or ad hoc environments
        TESTNET("hedera-testnet-streams-2023-01");

        private final String bucketName;

        public static HederaNetwork getHederaNetworkByName(@NonNull String network) {
            var delimiterIndex = network.indexOf(NETWORK_PREFIX_DELIMITER);
            var networkName = delimiterIndex < 0 ? network : network.substring(0, delimiterIndex);
            return valueOf(networkName.toUpperCase());
        }

        public boolean isAllowAnonymousAccess() {
            return this == DEMO;
        }

        public boolean is(String networkName) {
            try {
                return this == getHederaNetworkByName(networkName);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
    }
}
