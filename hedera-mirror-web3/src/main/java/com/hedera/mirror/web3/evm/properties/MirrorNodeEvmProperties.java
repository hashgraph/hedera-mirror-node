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

package com.hedera.mirror.web3.evm.properties;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_34;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_38;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes32;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

@Setter
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.evm")
public class MirrorNodeEvmProperties implements EvmProperties {

    @Getter
    private boolean allowTreasuryToOwnNfts = true;

    @NotNull
    private Set<EntityType> autoRenewTargetTypes = new HashSet<>();

    @Getter
    @Positive
    private long estimateGasIterationThreshold = 7300L;

    private boolean directTokenCall = true;

    private boolean dynamicEvmVersion = true;

    @Min(1)
    private long exchangeRateGasReq = 100;

    @NotBlank
    private String evmVersion = EVM_VERSION;

    private NavigableMap<Long, String> evmVersions = new TreeMap<>();

    @Getter
    @NotNull
    private EvmSpecVersion evmSpecVersion = EvmSpecVersion.SHANGHAI;

    @Getter
    @NotNull
    @DurationMin(seconds = 1)
    private Duration expirationCacheTime = Duration.ofMinutes(10L);

    @NotBlank
    private String fundingAccount = "0x0000000000000000000000000000000000000062";

    @Getter
    private long htsDefaultGasCost = 10000;

    @Getter
    private boolean limitTokenAssociations = false;

    @Getter
    @Min(1)
    private long maxAutoRenewDuration = 10000L;

    @Getter
    @Min(1)
    private int maxBatchSizeBurn = 10;

    @Getter
    @Min(1)
    private int maxBatchSizeMint = 10;

    @Getter
    @Min(1)
    private int maxBatchSizeWipe = 10;

    private int maxCustomFeesAllowed = 10;

    // maximum iteration count for estimate gas' search algorithm
    @Getter
    private int maxGasEstimateRetriesCount = 20;

    // used by eth_estimateGas only
    @Min(1)
    @Max(100)
    private int maxGasRefundPercentage = 100;

    @Getter
    @Min(1)
    private int maxMemoUtf8Bytes = 100;

    @Getter
    private int maxNftMetadataBytes = 100;

    @Getter
    @Min(1)
    private int maxTokenNameUtf8Bytes = 100;

    @Getter
    @Min(1)
    private int maxTokensPerAccount = 1000;

    @Getter
    @Min(1)
    private int maxTokenSymbolUtf8Bytes = 100;

    @Getter
    @Min(1)
    private long minAutoRenewDuration = 1000L;

    @Getter
    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    @Getter
    @Min(100)
    private long rateLimit = 500;

    public boolean shouldAutoRenewAccounts() {
        return autoRenewTargetTypes.contains(EntityType.ACCOUNT);
    }

    public boolean shouldAutoRenewContracts() {
        return autoRenewTargetTypes.contains(EntityType.CONTRACT);
    }

    public boolean shouldAutoRenewSomeEntityType() {
        return !autoRenewTargetTypes.isEmpty();
    }

    @Override
    public boolean isRedirectTokenCallsEnabled() {
        return directTokenCall;
    }

    @Override
    public boolean isLazyCreationEnabled() {
        return true;
    }

    @Override
    public boolean isCreate2Enabled() {
        return true;
    }

    @Override
    public boolean allowCallsToNonContractAccounts() {
        return false;
    }

    @Override
    public Set<Address> grandfatherContracts() {
        return new HashSet<>();
    }

    @Override
    public boolean callsToNonExistingEntitiesEnabled(Address target) {
        return false;
    }

    @Override
    public boolean dynamicEvmVersion() {
        return dynamicEvmVersion;
    }

    @Override
    public Bytes32 chainIdBytes32() {
        return network.getChainId();
    }

    @Override
    public String evmVersion() {
        var context = ContractCallContext.get();
        if (context.useHistorical()) {
            return getEvmVersionForBlock(context.getRecordFile().getIndex());
        }
        return evmVersion;
    }

    @Override
    public Address fundingAccountAddress() {
        return Address.fromHexString(fundingAccount);
    }

    @Override
    public int maxGasRefundPercentage() {
        return maxGasRefundPercentage;
    }

    public int maxCustomFeesAllowed() {
        return maxCustomFeesAllowed;
    }

    public long exchangeRateGasReq() {
        return exchangeRateGasReq;
    }

    @Getter
    @RequiredArgsConstructor
    public enum HederaNetwork {
        MAINNET(unhex("00"), Bytes32.fromHexString("0x0127"), mainnetEvmVersionsMap()),
        TESTNET(unhex("01"), Bytes32.fromHexString("0x0128"), Collections.emptyNavigableMap()),
        PREVIEWNET(unhex("02"), Bytes32.fromHexString("0x0129"), Collections.emptyNavigableMap()),
        OTHER(unhex("03"), Bytes32.fromHexString("0x012A"), Collections.emptyNavigableMap());

        private final byte[] ledgerId;
        private final Bytes32 chainId;
        private final NavigableMap<Long, String> evmVersions;

        private static NavigableMap<Long, String> mainnetEvmVersionsMap() {
            NavigableMap<Long, String> evmVersionsMap = new TreeMap<>();
            evmVersionsMap.put(0L, EVM_VERSION_0_30);
            evmVersionsMap.put(44029066L, EVM_VERSION_0_34);
            evmVersionsMap.put(49117794L, EVM_VERSION_0_38);
            return Collections.unmodifiableNavigableMap(evmVersionsMap);
        }
    }

    /**
     * Returns the most appropriate mapping of EVM versions
     * The method operates in a hierarchical manner:
     * 1. It initially attempts to use EVM versions defined in a YAML configuration.
     * 2. If no YAML configuration is available, it defaults to using EVM versions specified in the HederaNetwork enum.
     * 3. If no versions are defined in HederaNetwork, it falls back to a default map with an entry (0L, EVM_VERSION).
     * @return A NavigableMap<Long, String> representing the EVM versions. The key is the block number, and the value is the EVM version.
     */
    public NavigableMap<Long, String> getEvmVersions() {
        if (!CollectionUtils.isEmpty(evmVersions)) {
            return evmVersions;
        }

        if (!CollectionUtils.isEmpty(network.evmVersions)) {
            return network.evmVersions;
        }

        return new TreeMap<>(Map.of(0L, EVM_VERSION));
    }

    /**
     * Determines the most suitable EVM version for a given block number. This method
     * finds the highest EVM version whose block number is less than or equal to the specified block number. The determination
     * is based on the available EVM versions which are fetched using the getEvmVersions() method. If no specific version
     * matches the block number, it returns a default EVM version.
     * Note: This method relies on the hierarchical logic implemented in getEvmVersions() for fetching the EVM versions.
     *
     * @param blockNumber The block number for which the EVM version needs to be determined.
     * @return The most suitable EVM version for the given block number, or a default version if no specific match is found.
     */
    String getEvmVersionForBlock(long blockNumber) {
        Entry<Long, String> evmEntry = getEvmVersions().floorEntry(blockNumber);
        if (evmEntry != null) {
            return evmEntry.getValue();
        } else {
            return EVM_VERSION; // Return default version if no entry matches the block number
        }
    }
}
