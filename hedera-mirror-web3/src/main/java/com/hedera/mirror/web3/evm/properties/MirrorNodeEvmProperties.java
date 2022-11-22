package com.hedera.mirror.web3.evm.properties;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import lombok.Setter;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hedera.services.evm.contracts.execution.EvmProperties;

import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Setter
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.evm")
public class MirrorNodeEvmProperties implements EvmProperties {
    private boolean directTokenCall;

    private boolean dynamicEvmVersion;

    @NotBlank
    private String evmVersion = "";

    @NotBlank
    private String fundingAccount = "";

    @Min(1)
    @Max(100)
    private int maxGasRefundPercentage = 1;

    @Override
    public boolean isRedirectTokenCallsEnabled() {
        return directTokenCall;
    }

    @Override
    public boolean dynamicEvmVersion() {
        return dynamicEvmVersion;
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

}
