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

package com.hedera.mirror.web3.evm.properties;

import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.EVM_VERSION;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import java.time.Duration;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes32;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Setter
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.evm")
public class MirrorNodeEvmProperties implements EvmProperties {
    @Getter
    private boolean allowanceEnabled = false;

    @Getter
    private boolean approvedForAllEnabled = false;

    @NotNull
    private HederaChainId chainId = HederaChainId.TESTNET;

    @Getter
    @Positive
    private long estimateGasIterationThreshold = 3600;

    private boolean directTokenCall = true;

    private boolean dynamicEvmVersion = true;

    @NotBlank
    private String evmVersion = EVM_VERSION;

    @Getter
    @NotNull
    @DurationMin(seconds = 1)
    private Duration expirationCacheTime = Duration.ofMinutes(10L);

    @NotBlank
    private String fundingAccount = "0x0000000000000000000000000000000000000062";

    // maximum iteration count for estimate gas' search algorithm
    @Getter
    private int maxGasEstimateRetriesCount = 20;

    // used by eth_estimateGas only
    @Min(1)
    @Max(100)
    private int maxGasRefundPercentage = 100;

    @Getter
    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    @Getter
    @NotNull
    @DurationMin(seconds = 100)
    private Duration rateLimit = Duration.ofSeconds(100L);

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
    public boolean dynamicEvmVersion() {
        return dynamicEvmVersion;
    }

    @Override
    public Bytes32 chainIdBytes32() {
        return Bytes32.fromHexString(chainId.getChainId());
    }

    @Override
    public String evmVersion() {
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

    @Getter
    @RequiredArgsConstructor
    public enum HederaNetwork {
        MAINNET(unhex("00")),
        TESTNET(unhex("01")),
        PREVIEWNET(unhex("02")),
        OTHER(unhex("03"));

        private final byte[] ledgerId;
    }

    @Getter
    @RequiredArgsConstructor
    public enum HederaChainId {
        MAINNET("0x0127"),
        TESTNET("0x0128"),
        PREVIEWNET("0x0129"),
        OTHER("0x12A");

        private final String chainId;
    }
}
