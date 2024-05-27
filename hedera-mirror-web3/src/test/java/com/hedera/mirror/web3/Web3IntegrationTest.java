/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;

import com.hedera.mirror.common.config.CommonIntegrationTest;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import jakarta.annotation.Resource;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
public abstract class Web3IntegrationTest extends CommonIntegrationTest {

    protected static final long EXPIRY = 1_234_567_890L;

    @Resource
    protected Store store;

    protected CallServiceParameters serviceParametersForExecution(
            final Bytes callData,
            final Address contractAddress,
            final CallType callType,
            final long value,
            final BlockType block,
            final Address senderAddress) {
        HederaEvmAccount sender;
        if (block != BlockType.LATEST) {
            final var senderAddressHistorical = toAddress(EntityId.of(0, 0, 1014));
            sender = new HederaEvmAccount(senderAddressHistorical);
        } else {
            sender = new HederaEvmAccount(senderAddress);
        }

        persistEntities();

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(contractAddress)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(block)
                .build();
    }

    protected void persistEntities() {}
}
