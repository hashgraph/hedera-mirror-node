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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ModularizedProperties {

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    private static final String EVM_PREFIX = "v";

    private static final String AUTO_RENEW_TARGET_TYPES = "autoRenew.targetTypes";

    private static final String CHAIN_ID_BYTES32 = "contracts.chainId";

    private static final String EVM_VERSION = "contracts.evm.version";

    private static final String EVM_VERSION_DYNAMIC = "contracts.evm.version.dynamic";

    private static final String FUNDING_ACCOUNT_ADDRESS = "ledger.fundingAccount";

    private static final String MAX_GAS_REFUND_PERCENTAGE = "contracts.maxRefundPercentOfGasLimit";

    private static final String DIRECT_TOKEN_CALL = "contracts.redirectTokenCalls";

    private static final String LAZY_CREATION = "lazyCreation.enabled";

    private static final String CREATE2_ENABLED = "contracts.allowCreate2";

    private static final String ALLOW_CALLS_TO_NON_CONTRACT_ACCOUNTS = "contracts.evm.allowCallsToNonContractAccounts";

    private static final String GRANDFATHER_CONTRACTS = "contracts.evm.nonExtantContractsFail";

    private static final String EXCHANGE_RATE_GAS_COST = "contracts.precompile.exchangeRateGasCost";

    private static final String HTS_DEFAULT_GAS_COST = "contracts.precompile.htsDefaultGasCost";

    private static final String LIMIT_TOKEN_ASSOCIATIONS = "entities.limitTokenAssociations";

    private static final String MAX_AUTO_RENEW_DURATION = "entities.maxLifetime";

    private static final String MAX_BATCH_SIZE_BURN = "token.nfts.maxBatchSizeBurn";

    private static final String MAX_BATCH_SIZE_MINT = "token.nfts.maxBatchSizeMint";

    private static final String MAX_BATCH_SIZE_WIPE = "token.nfts.maxBatchSizeWipe";

    private static final String MAX_CUSTOM_FEES_ALLOWED = "tokens.maxCustomFeesAllowed";

    private static final String MAX_MEMO_UTF8_BYTES = "tokens.transaction.maxMemoUtf8Bytes";

    private static final String MAX_NFT_METADATA_BYTES = "tokens.nfts.maxMetadataBytes";

    private static final String MAX_TOKEN_NAME_UTF8_BYTES = "tokens.maxTokenNameUtf8Bytes";

    private static final String MAX_TOKENS_PER_ACCOUNT = "tokens.maxPerAccount";

    private static final String MAX_TOKEN_SYMBOL_UTF8_BYTES = "tokens.maxSymbolUtf8Bytes";

    private static final String FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER = "fees.tokenTransferUsageMultiplier";

    /**
     * Converts values from {@link MirrorNodeEvmProperties} to a map of property values. This map contains configuration
     * keys and values that will be passed as properties to hedera-app transaction executors.
     *
     * @return a map containing the configuration keys as keys and their corresponding values as strings.
     */
    public Map<String, String> getPropertiesMap() {
        Map<String, String> propertiesMap = new HashMap<>();
        propertiesMap.put(
                AUTO_RENEW_TARGET_TYPES,
                mirrorNodeEvmProperties.getAutoRenewTargetTypes().toString());
        propertiesMap.put(
                CHAIN_ID_BYTES32,
                String.valueOf(
                        mirrorNodeEvmProperties.chainIdBytes32().toBigInteger().toString()));
        propertiesMap.put(EVM_VERSION, getEvmVersion());
        propertiesMap.put(EVM_VERSION_DYNAMIC, String.valueOf(mirrorNodeEvmProperties.dynamicEvmVersion()));
        propertiesMap.put(
                FUNDING_ACCOUNT_ADDRESS,
                mirrorNodeEvmProperties.fundingAccountAddress().toBigInteger().toString());
        propertiesMap.put(MAX_GAS_REFUND_PERCENTAGE, String.valueOf(mirrorNodeEvmProperties.maxGasRefundPercentage()));
        propertiesMap.put(DIRECT_TOKEN_CALL, String.valueOf(mirrorNodeEvmProperties.isRedirectTokenCallsEnabled()));
        propertiesMap.put(LAZY_CREATION, String.valueOf(mirrorNodeEvmProperties.isLazyCreationEnabled()));
        propertiesMap.put(CREATE2_ENABLED, String.valueOf(mirrorNodeEvmProperties.isCreate2Enabled()));
        propertiesMap.put(
                ALLOW_CALLS_TO_NON_CONTRACT_ACCOUNTS,
                String.valueOf(mirrorNodeEvmProperties.allowCallsToNonContractAccounts()));
        propertiesMap.put(
                GRANDFATHER_CONTRACTS,
                mirrorNodeEvmProperties.grandfatherContracts().toString());
        propertiesMap.put(EXCHANGE_RATE_GAS_COST, String.valueOf(mirrorNodeEvmProperties.exchangeRateGasReq()));
        propertiesMap.put(HTS_DEFAULT_GAS_COST, String.valueOf(mirrorNodeEvmProperties.getHtsDefaultGasCost()));
        propertiesMap.put(LIMIT_TOKEN_ASSOCIATIONS, String.valueOf(mirrorNodeEvmProperties.isLimitTokenAssociations()));
        propertiesMap.put(MAX_AUTO_RENEW_DURATION, String.valueOf(mirrorNodeEvmProperties.getMaxAutoRenewDuration()));
        propertiesMap.put(MAX_BATCH_SIZE_BURN, String.valueOf(mirrorNodeEvmProperties.getMaxBatchSizeBurn()));
        propertiesMap.put(MAX_BATCH_SIZE_MINT, String.valueOf(mirrorNodeEvmProperties.getMaxBatchSizeMint()));
        propertiesMap.put(MAX_BATCH_SIZE_WIPE, String.valueOf(mirrorNodeEvmProperties.getMaxBatchSizeWipe()));
        propertiesMap.put(MAX_CUSTOM_FEES_ALLOWED, String.valueOf(mirrorNodeEvmProperties.maxCustomFeesAllowed()));
        propertiesMap.put(MAX_MEMO_UTF8_BYTES, String.valueOf(mirrorNodeEvmProperties.getMaxMemoUtf8Bytes()));
        propertiesMap.put(MAX_NFT_METADATA_BYTES, String.valueOf(mirrorNodeEvmProperties.getMaxNftMetadataBytes()));
        propertiesMap.put(
                MAX_TOKEN_NAME_UTF8_BYTES, String.valueOf(mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes()));
        propertiesMap.put(MAX_TOKENS_PER_ACCOUNT, String.valueOf(mirrorNodeEvmProperties.getMaxTokensPerAccount()));
        propertiesMap.put(
                MAX_TOKEN_SYMBOL_UTF8_BYTES, String.valueOf(mirrorNodeEvmProperties.getMaxTokenSymbolUtf8Bytes()));
        propertiesMap.put(
                FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER,
                String.valueOf(mirrorNodeEvmProperties.feesTokenTransferUsageMultiplier()));
        return Collections.unmodifiableMap(propertiesMap);
    }

    private String getEvmVersion() {
        return EVM_PREFIX + mirrorNodeEvmProperties.getEvmVersion().major() + "."
                + mirrorNodeEvmProperties.getEvmVersion().minor();
    }
}
