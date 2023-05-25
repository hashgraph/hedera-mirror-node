/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_ALLOW_CREATE2;
import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_CHAIN_ID;
import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_DYNAMIC_EVM_VERSION;
import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_EVM_VERSION;
import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT;
import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_REDIRECT_TOKEN_CALLS;
import static com.hedera.services.context.properties.PropertyNames.LAZY_CREATION_ENABLED;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_FUNDING_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.STAKING_FEES_NODE_REWARD_PERCENT;
import static com.hedera.services.context.properties.PropertyNames.STAKING_FEES_STAKING_REWARD_PERCENT;
import static com.hedera.services.context.properties.PropertyNames.STAKING_IS_ENABLED;

@Singleton
public class GlobalDynamicProperties implements EvmProperties {

    private final HederaNumbers hederaNums;
    private final PropertySource properties;

    private AccountID fundingAccount;
    private Address fundingAccountAddress;
    private byte[] chainIdBytes;
    private Bytes32 chainIdBytes32;
    private String evmVersion;
    private boolean dynamicEvmVersion;
    private int contractMaxRefundPercentOfGasLimit;
    private boolean create2Enabled;
    private boolean redirectTokenCalls;
    private int nodeRewardPercent;
    private int stakingRewardPercent;
    private boolean stakingEnabled;
    private boolean lazyCreationEnabled;

    @Inject
    public GlobalDynamicProperties(final HederaNumbers hederaNums, @CompositeProps final PropertySource properties) {
        this.hederaNums = hederaNums;
        this.properties = properties;

        reload();
    }

    public void reload() {
        fundingAccount = AccountID.newBuilder()
                .setShardNum(hederaNums.shard())
                .setRealmNum(hederaNums.realm())
                .setAccountNum(properties.getLongProperty(LEDGER_FUNDING_ACCOUNT))
                .build();
        fundingAccountAddress = EntityIdUtils.asTypedEvmAddress(fundingAccount);
        final var chainId = properties.getIntProperty(CONTRACTS_CHAIN_ID);
        chainIdBytes = Integers.toBytes(chainId);
        chainIdBytes32 = Bytes32.leftPad(Bytes.of(chainIdBytes));
        dynamicEvmVersion = properties.getBooleanProperty(CONTRACTS_DYNAMIC_EVM_VERSION);
        evmVersion = properties.getStringProperty(CONTRACTS_EVM_VERSION);
        contractMaxRefundPercentOfGasLimit = properties.getIntProperty(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT);
        create2Enabled = properties.getBooleanProperty(CONTRACTS_ALLOW_CREATE2);
        redirectTokenCalls = properties.getBooleanProperty(CONTRACTS_REDIRECT_TOKEN_CALLS);
        nodeRewardPercent = properties.getIntProperty(STAKING_FEES_NODE_REWARD_PERCENT);
        stakingRewardPercent = properties.getIntProperty(STAKING_FEES_STAKING_REWARD_PERCENT);
        stakingEnabled = properties.getBooleanProperty(STAKING_IS_ENABLED);
        lazyCreationEnabled = properties.getBooleanProperty(LAZY_CREATION_ENABLED);
    }

    public AccountID fundingAccount() {
        return fundingAccount;
    }

    public Address fundingAccountAddress() {
        return fundingAccountAddress;
    }

    public Bytes32 chainIdBytes32() {
        return chainIdBytes32;
    }

    public String evmVersion() {
        return evmVersion;
    }

    public boolean dynamicEvmVersion() {
        return dynamicEvmVersion;
    }

    public int maxGasRefundPercentage() {
        return contractMaxRefundPercentOfGasLimit;
    }

    public boolean isCreate2Enabled() {
        return create2Enabled;
    }

    public boolean isRedirectTokenCallsEnabled() {
        return redirectTokenCalls;
    }

    public int getNodeRewardPercent() {
        return nodeRewardPercent;
    }

    public int getStakingRewardPercent() {
        return stakingRewardPercent;
    }

    public boolean isStakingEnabled() {
        return stakingEnabled;
    }

    public boolean isLazyCreationEnabled() {
        return lazyCreationEnabled;
    }
}
