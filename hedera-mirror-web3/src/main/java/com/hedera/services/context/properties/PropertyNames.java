/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.context.properties;

public class PropertyNames {

    private PropertyNames() {
        /* No-Op */
    }

    /* ---- Bootstrap properties ---- */

    /* ---- Global Static properties ---- */
    public static final String HEDERA_REALM = "hedera.realm";
    public static final String HEDERA_SHARD = "hedera.shard";

    /* ---- Global dynamic properties ---- */
    public static final String LAZY_CREATION_ENABLED = "lazyCreation.enabled";
    public static final String CONTRACTS_ALLOW_CREATE2 = "contracts.allowCreate2";
    public static final String CONTRACTS_CHAIN_ID = "contracts.chainId";
    public static final String CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT = "contracts.maxRefundPercentOfGasLimit";
    public static final String CONTRACTS_REDIRECT_TOKEN_CALLS = "contracts.redirectTokenCalls";
    public static final String CONTRACTS_DYNAMIC_EVM_VERSION = "contracts.evm.version.dynamic";
    public static final String CONTRACTS_EVM_VERSION = "contracts.evm.version";
    public static final String LEDGER_FUNDING_ACCOUNT = "ledger.fundingAccount";
    public static final String STAKING_FEES_NODE_REWARD_PERCENT = "staking.fees.nodeRewardPercentage";
    public static final String STAKING_FEES_STAKING_REWARD_PERCENT = "staking.fees.stakingRewardPercentage";
    public static final String STAKING_IS_ENABLED = "staking.isEnabled";

    /* ---- Node properties ----- */
}
