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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class MirrorNodeEvmPropertiesTest extends Web3IntegrationTest {
    private static final String EVM_VERSION = "v0.34";
    private static final int MAX_REFUND_PERCENT = 100;
    private static final Address FUNDING_ADDRESS = Address.fromHexString("0x0000000000000000000000000000000000000062");
    private static final Bytes32 CHAIN_ID = Bytes32.fromHexString("0x0128");

    private final MirrorNodeEvmProperties properties;

    @Test
    void correctPropertiesEvaluation() {
        assertThat(properties.evmVersion()).isEqualTo(EVM_VERSION);
        assertThat(properties.dynamicEvmVersion()).isTrue();
        assertThat(properties.maxGasRefundPercentage()).isEqualTo(MAX_REFUND_PERCENT);
        assertThat(properties.fundingAccountAddress()).isEqualTo(FUNDING_ADDRESS);
        assertThat(properties.isRedirectTokenCallsEnabled()).isTrue();
        assertThat(properties.isLazyCreationEnabled()).isTrue();
        assertThat(properties.isCreate2Enabled()).isTrue();
        assertThat(properties.chainIdBytes32()).isEqualTo(CHAIN_ID);
        assertThat(properties.isLimitTokenAssociations()).isFalse();
        assertThat(properties.shouldAutoRenewAccounts()).isFalse();
        assertThat(properties.shouldAutoRenewContracts()).isFalse();
        assertThat(properties.shouldAutoRenewSomeEntityType()).isFalse();
    }
}
