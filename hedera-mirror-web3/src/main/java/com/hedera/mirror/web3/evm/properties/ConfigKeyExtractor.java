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

import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.ConfigData;
import java.lang.reflect.Field;

/**
 * The ConfigKeyExtractor class reads configuration keys from the modularized services code and returns them.
 */
public class ConfigKeyExtractor {

    public static final String DOT_SEPARATOR = ".";
    public static final String CHAIN_ID_KEY = "chainId";
    public static final String MAX_GAS_REFUND_PERCENTAGE_KEY = "maxRefundPercentOfGasLimit";

    public static String getChainIdKey() {
        return getConfigKey(ContractsConfig.class, CHAIN_ID_KEY);
    }

    public static String getMaxGasRefundPercentageKey() {
        return getConfigKey(ContractsConfig.class, MAX_GAS_REFUND_PERCENTAGE_KEY);
    }

    public static String getConfigKey(Class<?> configClass, String fieldName) {
        try {
            Field field = configClass.getDeclaredField(fieldName);
            ConfigData configProperty = configClass.getAnnotation(ConfigData.class);
            return configProperty.value() + DOT_SEPARATOR + field.getName();
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Field " + fieldName + " not found in " + configClass.getSimpleName());
        }
    }
}
