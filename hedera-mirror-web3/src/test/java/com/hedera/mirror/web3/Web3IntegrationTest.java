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

import static com.hedera.mirror.web3.service.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.config.CommonIntegrationTest;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import jakarta.annotation.Resource;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;

@ExtendWith(ContextExtension.class)
public abstract class Web3IntegrationTest extends CommonIntegrationTest {

    @SpyBean
    protected MirrorEvmTxProcessor processor;

    @Resource
    protected Store store;

    @SuppressWarnings("try")
    protected long gasUsedAfterExecution(final ContractExecutionParameters serviceParameters) {
        return ContractCallContext.run(ctx -> {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            long result = processor
                    .execute(serviceParameters, serviceParameters.getGas())
                    .getGasUsed();

            assertThat(store.getStackedStateFrames().height()).isEqualTo(1);
            return result;
        });
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<TransactionReceipt> functionCall, final Contract contract) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(Bytes.fromHexString(functionCall.encodeFunctionCall()))
                .callType(CallType.ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                .sender(new HederaEvmAccount(Address.wrap(Bytes.wrap(domainBuilder.evmAddress()))))
                .value(0L)
                .build();
    }
}
