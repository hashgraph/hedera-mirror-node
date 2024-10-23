/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.properties.ConfigKeyExtractor.CHAIN_ID_KEY;
import static com.hedera.mirror.web3.evm.properties.ConfigKeyExtractor.DOT_SEPARATOR;
import static com.hedera.mirror.web3.evm.properties.ConfigKeyExtractor.MAX_GAS_REFUND_PERCENTAGE_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.config.data.ContractsConfig;
import org.junit.jupiter.api.Test;

class ConfigKeyExtractorTest {

    private static final String CONTRACT_CONFIG_KEY = "contracts";
    private static final String CONTRACTS_CHAIN_ID_KEY = CONTRACT_CONFIG_KEY + DOT_SEPARATOR + CHAIN_ID_KEY;
    private static final String CONTRACTS_MAX_GAS_REFUND_PERCENTAGE_KEY =
            CONTRACT_CONFIG_KEY + DOT_SEPARATOR + MAX_GAS_REFUND_PERCENTAGE_KEY;

    @Test
    void testGetChainIdKey() {
        String actualChainId = ConfigKeyExtractor.getChainIdKey();
        String actualMaxGasRefundPercentage = ConfigKeyExtractor.getMaxGasRefundPercentageKey();
        assertThat(actualChainId).isEqualTo(CONTRACTS_CHAIN_ID_KEY);
        assertThat(actualMaxGasRefundPercentage).isEqualTo(CONTRACTS_MAX_GAS_REFUND_PERCENTAGE_KEY);
    }

    @Test
    void testGetConfigKeyWithoutConfigProperty() {
        String actualChainId = ConfigKeyExtractor.getConfigKey(ContractsConfig.class, CHAIN_ID_KEY);
        String actualMaxGasRefundPercentage =
                ConfigKeyExtractor.getConfigKey(ContractsConfig.class, MAX_GAS_REFUND_PERCENTAGE_KEY);
        assertThat(actualChainId).isEqualTo(CONTRACTS_CHAIN_ID_KEY);
        assertThat(actualMaxGasRefundPercentage).isEqualTo(CONTRACTS_MAX_GAS_REFUND_PERCENTAGE_KEY);
    }

    @Test
    void testGetConfigKeyFieldNotFound() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigKeyExtractor.getConfigKey(ContractsConfig.class, "nonExistentField"));
    }
}
