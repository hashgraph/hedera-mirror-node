/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.ConfigData;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class MirrorNodeEvmPropertiesIntegrationTest extends Web3IntegrationTest {

    private static final String DOT_SEPARATOR = ".";
    private static final String CHAIN_ID = "chainId";
    private static final String CONTRACTS_CONFIG = "contracts";
    private static final String CHAIN_ID_KEY_CONFIG = CONTRACTS_CONFIG + DOT_SEPARATOR + CHAIN_ID;
    private static final Map<String, String> YAML_PROPERTIES =
            Map.of("contracts.allowCreate2", "false", CHAIN_ID_KEY_CONFIG, "297");
    private static final String MAX_GAS_REFUND_PERCENTAGE = "maxRefundPercentOfGasLimit";
    private static final String MAX_GAS_REFUND_PERCENTAGE_KEY_CONFIG =
            CONTRACTS_CONFIG + DOT_SEPARATOR + MAX_GAS_REFUND_PERCENTAGE;
    private final MirrorNodeEvmProperties properties;

    private static String getConfigKey(final Class<?> configClass, final String fieldName) {
        try {
            Field field = configClass.getDeclaredField(fieldName);
            ConfigData configProperty = configClass.getAnnotation(ConfigData.class);
            return configProperty.value() + DOT_SEPARATOR + field.getName();
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Field " + fieldName + " not found in " + configClass.getSimpleName());
        }
    }

    private static String getContractsConfigKey(final String configKey) {
        var fieldName = configKey.replace(CONTRACTS_CONFIG + DOT_SEPARATOR, "");
        return getConfigKey(ContractsConfig.class, fieldName);
    }

    @Test
    void getModularizedProperties() {
        Map<String, String> modularizedProperties = properties.getProperties();
        assertThat(modularizedProperties)
                .isNotEmpty()
                // override from yaml
                .doesNotContainEntry(CHAIN_ID_KEY_CONFIG, "296")
                .containsEntry(MAX_GAS_REFUND_PERCENTAGE_KEY_CONFIG, "100")
                .containsAllEntriesOf(YAML_PROPERTIES);
    }

    @Test
    void verifyUpstreamPropertiesExist() {
        Set<String> propertyKeys = properties.getProperties().keySet();
        propertyKeys.forEach(
                configKey -> assertThat(getContractsConfigKey(configKey)).isEqualTo(configKey));
    }

    @Test
    void verifyUpstreamNonExistentProperty() {
        assertThrows(IllegalArgumentException.class, () -> getConfigKey(ContractsConfig.class, "nonExistentField"));
    }
}
