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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
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

    @NotBlank
    private String network = HederaNetwork.DEMO;

    @Min(0)
    private long shard = 0L;

    private Instant startDate;

    private Long startBlockNumber;

    private Long topicRunningHashV2AddedTimestamp;

    @NotNull
    private Instant verifyHashAfter = Instant.EPOCH;

    public String getNetwork() {
        return StringUtils.substringBefore(this.network, NETWORK_PREFIX_DELIMITER)
                .toLowerCase();
    }

    public String getNetworkPrefix() {
        var networkPrefix = StringUtils.substringAfter(this.network, NETWORK_PREFIX_DELIMITER);
        return StringUtils.isEmpty(networkPrefix) ? null : networkPrefix.toLowerCase();
    }

    public enum ConsensusMode {
        EQUAL, // all nodes equally weighted
        STAKE, // all nodes specify their node stake
        STAKE_IN_ADDRESS_BOOK // like STAKE, but only the nodes found in the address book are used in the calculation.
    }

    public final class HederaNetwork {
        public static final String DEMO = "demo";
        public static final String MAINNET = "mainnet";
        public static final String OTHER = "other";
        public static final String PREVIEWNET = "previewnet";
        public static final String TESTNET = "testnet";

        private static final Map<String, String> NETWORK_DEFAULT_BUCKETS = Map.of(
                DEMO, "hedera-demo-streams",
                MAINNET, "hedera-mainnet-streams",
                // OTHER has no default bucket
                PREVIEWNET, "hedera-preview-testnet-streams",
                TESTNET, "hedera-testnet-streams-2023-01");

        private HederaNetwork() {}

        public static String getBucketName(@NonNull String network) {
            return NETWORK_DEFAULT_BUCKETS.getOrDefault(network, "");
        }

        public static boolean isAllowAnonymousAccess(@NonNull String network) {
            return DEMO.equals(network);
        }
    }
}
