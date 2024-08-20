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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_LIMIT_METRIC;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.web3.web3j.generated.InternalCaller;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class InternalCallsTest extends AbstractContractCallServiceTest {

    private static final String NON_EXISTING_ADDRESS = toAddress(123456789L).toHexString();

    @Test
    void callToNonExistingContract() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        meterRegistry.clear();
        final var result = contract.call_callNonExisting(NON_EXISTING_ADDRESS).send();

        assertThat(Bytes.wrap(result.component2())).isEqualTo(Bytes.EMPTY);
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void callToNonExistingFunction() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        meterRegistry.clear();
        final var result =
                contract.call_callNonExisting(contract.getContractAddress()).send();

        assertThat(Bytes.wrap(result.component2())).isEqualTo(Bytes.EMPTY);
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void callToNonExistingFunctionWithValue() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        meterRegistry.clear();
        final var result =
                contract.call_callWithValueTo(contract.getContractAddress()).send();

        assertThat(Bytes.wrap(result.component2())).isEqualTo(Bytes.EMPTY);
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void sendToNonExistingAccount() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        meterRegistry.clear();
        final var result = contract.call_sendTo(NON_EXISTING_ADDRESS).send();

        assertThat(result).isEqualTo(Boolean.TRUE);
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void transferToNonExistingAccount() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        meterRegistry.clear();
        contract.send_transferTo(NON_EXISTING_ADDRESS).send();

        assertThat(testWeb3jService.getTransactionResult()).isEqualTo(HEX_PREFIX);
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    private void assertGasLimit(final long gasLimit) {
        final var counter = meterRegistry.find(GAS_LIMIT_METRIC).counters().stream()
                .filter(c -> ETH_CALL.name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        assertThat(counter.count()).isEqualTo(gasLimit);
    }
}
