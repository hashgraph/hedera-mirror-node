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

package com.hedera.mirror.web3.service.utils;

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.web3.service.ContractCallTestSetup;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class ContractCallEvmCodesTest extends ContractCallTestSetup {

    @Test
    void chainId() {
        final var functionHash = functionEncodeDecoder.functionHashFor("chainId", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(properties.chainIdBytes32().toHexString());
    }

    private CallServiceParameters serviceParametersForEvmCodes(final Bytes callData) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        persistEntities();

        return CallServiceParameters.builder()
                .sender(sender)
                .value(0L)
                .receiver(EVM_CODES_CONTRACT_ADDRESS)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(true)
                .callType(ETH_CALL)
                .build();
    }
}
