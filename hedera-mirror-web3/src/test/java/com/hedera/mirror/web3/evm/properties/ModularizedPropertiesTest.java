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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties.HederaNetwork;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
public class ModularizedPropertiesTest extends Web3IntegrationTest {

    private final ModularizedProperties modularizedProperties;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    private static final String AUTO_RENEW_TARGET_TYPES = "autoRenew.targetTypes";
    private static final String CHAIN_ID = "contracts.chainId";
    private static final String EVM_VERSION = "contracts.evm.version";
    private static final String EVM_VERSION_DYNAMIC = "contracts.evm.version.dynamic";
    private static final String FUNDING_ACCOUNT = "ledger.fundingAccount";
    private static final String FUNDING_ACCOUNT_VALUE = "0x0000000000000000000000000000000000000062";
    private static final String MAX_REFUND_PERCENT = "contracts.maxRefundPercentOfGasLimit";
    private static final String REDIRECT_TOKEN_CALLS = "contracts.redirectTokenCalls";
    private static final String LAZY_CREATION_ENABLED = "lazyCreation.enabled";
    private static final String ALLOW_CREATE2 = "contracts.allowCreate2";
    private static final String ALLOW_CALLS_TO_NON_CONTRACT = "contracts.evm.allowCallsToNonContractAccounts";
    private static final String MAX_BATCH_SIZE_BURN = "token.nfts.maxBatchSizeBurn";
    private static final String MAX_BATCH_SIZE_MINT = "token.nfts.maxBatchSizeMint";
    private static final String MAX_BATCH_SIZE_WIPE = "token.nfts.maxBatchSizeWipe";
    private static final String NON_EXTANT_CONTRACTS_FAIL = "contracts.evm.nonExtantContractsFail";
    private static final String EXCHANGE_RATE_GAS_COST = "contracts.precompile.exchangeRateGasCost";
    private static final String HTS_DEFAULT_GAS_COST = "contracts.precompile.htsDefaultGasCost";
    private static final String LIMIT_TOKEN_ASSOCIATIONS = "entities.limitTokenAssociations";
    private static final String MAX_LIFETIME = "entities.maxLifetime";
    private static final String MAX_CUSTOM_FEES_ALLOWED = "tokens.maxCustomFeesAllowed";
    private static final String MAX_MEMO_UTF8_BYTES = "tokens.transaction.maxMemoUtf8Bytes";
    private static final String MAX_METADATA_BYTES = "tokens.nfts.maxMetadataBytes";
    private static final String MAX_TOKEN_NAME_UTF8_BYTES = "tokens.maxTokenNameUtf8Bytes";
    private static final String MAX_PER_ACCOUNT = "tokens.maxPerAccount";
    private static final String MAX_SYMBOL_UTF8_BYTES = "tokens.maxSymbolUtf8Bytes";
    private static final String TOKEN_TRANSFER_USAGE_MULTIPLIER = "fees.tokenTransferUsageMultiplier";

    @BeforeEach
    public void initializeProperties() {
        mirrorNodeEvmProperties.setAutoRenewTargetTypes(Set.of(EntityType.ACCOUNT));
        mirrorNodeEvmProperties.setNetwork(HederaNetwork.MAINNET);
        mirrorNodeEvmProperties.setEvmVersion(
                SemanticVersion.newBuilder().major(0).minor(46).patch(0).build());
        mirrorNodeEvmProperties.setDynamicEvmVersion(true);
        mirrorNodeEvmProperties.setFundingAccount(FUNDING_ACCOUNT_VALUE);
        mirrorNodeEvmProperties.setMaxGasRefundPercentage(20);
        mirrorNodeEvmProperties.setDirectTokenCall(false);
        mirrorNodeEvmProperties.setMaxBatchSizeBurn(10);
        mirrorNodeEvmProperties.setMaxBatchSizeMint(11);
        mirrorNodeEvmProperties.setMaxBatchSizeWipe(12);
        mirrorNodeEvmProperties.setExchangeRateGasReq(101);
        mirrorNodeEvmProperties.setHtsDefaultGasCost(10001);
        mirrorNodeEvmProperties.setLimitTokenAssociations(true);
        mirrorNodeEvmProperties.setMaxAutoRenewDuration(8000002);
        mirrorNodeEvmProperties.setMaxCustomFeesAllowed(11);
        mirrorNodeEvmProperties.setMaxMemoUtf8Bytes(101);
        mirrorNodeEvmProperties.setMaxNftMetadataBytes(101);
        mirrorNodeEvmProperties.setMaxTokenNameUtf8Bytes(101);
        mirrorNodeEvmProperties.setMaxTokensPerAccount(1001);
        mirrorNodeEvmProperties.setMaxTokenSymbolUtf8Bytes(101);
        mirrorNodeEvmProperties.setFeesTokenTransferUsageMultiplier(381);
    }

    @Test
    public void testGetPropertiesMap() {
        Map<String, String> propertiesMap = modularizedProperties.getPropertiesMap();

        assertThat(propertiesMap)
                .isNotEmpty()
                .containsAllEntriesOf(Map.of(
                        AUTO_RENEW_TARGET_TYPES,
                        "[ACCOUNT]",
                        CHAIN_ID,
                        "295",
                        EVM_VERSION,
                        "v0.46",
                        EVM_VERSION_DYNAMIC,
                        "true",
                        FUNDING_ACCOUNT,
                        "98",
                        MAX_REFUND_PERCENT,
                        "20",
                        REDIRECT_TOKEN_CALLS,
                        "false",
                        LAZY_CREATION_ENABLED,
                        "true",
                        ALLOW_CREATE2,
                        "true",
                        ALLOW_CALLS_TO_NON_CONTRACT,
                        "true"))
                .containsAllEntriesOf(Map.of(
                        MAX_BATCH_SIZE_BURN,
                        "10",
                        MAX_BATCH_SIZE_MINT,
                        "11",
                        MAX_BATCH_SIZE_WIPE,
                        "12",
                        NON_EXTANT_CONTRACTS_FAIL,
                        "[]",
                        EXCHANGE_RATE_GAS_COST,
                        "101",
                        HTS_DEFAULT_GAS_COST,
                        "10001",
                        LIMIT_TOKEN_ASSOCIATIONS,
                        "true",
                        MAX_LIFETIME,
                        "8000002",
                        MAX_CUSTOM_FEES_ALLOWED,
                        "11",
                        MAX_MEMO_UTF8_BYTES,
                        "101"))
                .containsAllEntriesOf(Map.of(
                        MAX_METADATA_BYTES,
                        "101",
                        MAX_TOKEN_NAME_UTF8_BYTES,
                        "101",
                        MAX_PER_ACCOUNT,
                        "1001",
                        MAX_SYMBOL_UTF8_BYTES,
                        "101",
                        TOKEN_TRANSFER_USAGE_MULTIPLIER,
                        "381"));
    }
}
