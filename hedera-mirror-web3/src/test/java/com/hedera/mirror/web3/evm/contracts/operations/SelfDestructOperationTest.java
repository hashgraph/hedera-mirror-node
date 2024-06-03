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

package com.hedera.mirror.web3.evm.contracts.operations;

import static com.hedera.mirror.web3.service.model.BaseCallServiceParameters.CallType.ETH_CALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.ContractCallTestSetup;
import com.hedera.mirror.web3.viewmodel.BlockType;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class SelfDestructOperationTest extends ContractCallTestSetup {

    @Test
    void testSuccesfullExecute() {
        final var destroyContractInput = "0x9a0313ab000000000000000000000000" + SENDER_ALIAS.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(destroyContractInput),
                SELF_DESTRUCT_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST);
        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
    }

    @Test
    void testExecuteWithInvalidOwner() {
        final var destroyContractInput =
                "0x9a0313ab000000000000000000000000" + SYSTEM_ACCOUNT_ADDRESS.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(destroyContractInput),
                SELF_DESTRUCT_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST);

        assertEquals(
                INVALID_SOLIDITY_ADDRESS.name(),
                assertThrows(
                                MirrorEvmTransactionException.class,
                                () -> contractCallService.processCall(serviceParameters))
                        .getMessage());
    }
}
