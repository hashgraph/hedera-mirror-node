package com.hedera.mirror.web3.service;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractCallServiceTest extends Web3IntegrationTest {
    private static final Bytes RUNTIME_BYTES = Bytes.fromHexString(
            "6080604052600436106100555760003560e01c80631079023a1461005a578063289ec69b146100975780632e64cec1146100b55780635a43c50c146100e0578063c32b6ba11461011d578063e84326d61461015a575b600080fd5b34801561006657600080fd5b50610081600480360381019061007c919061021c565b610164565b60405161008e9190610282565b60405180910390f35b61009f61017a565b6040516100ac9190610267565b60405180910390f35b3480156100c157600080fd5b506100ca610183565b6040516100d79190610282565b60405180910390f35b3480156100ec57600080fd5b506101076004803603810190610102919061021c565b61018c565b6040516101149190610282565b60405180910390f35b34801561012957600080fd5b50610144600480360381019061013f91906101ef565b6101a2565b6040516101519190610282565b60405180910390f35b6101626101c3565b005b6000600282610173919061029d565b9050919050565b60006001905090565b60006048905090565b600080548261019b919061029d565b9050919050565b60008173ffffffffffffffffffffffffffffffffffffffff16319050919050565b565b6000813590506101d48161036f565b92915050565b6000813590506101e981610386565b92915050565b6000602082840312156102055761020461036a565b5b6000610213848285016101c5565b91505092915050565b6000602082840312156102325761023161036a565b5b6000610240848285016101da565b91505092915050565b61025281610305565b82525050565b61026181610331565b82525050565b600060208201905061027c6000830184610249565b92915050565b60006020820190506102976000830184610258565b92915050565b60006102a882610331565b91506102b383610331565b9250827fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff038211156102e8576102e761033b565b5b828201905092915050565b60006102fe82610311565b9050919050565b60008115159050919050565b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b6000819050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601160045260246000fd5b600080fd5b610378816102f3565b811461038357600080fd5b50565b61038f81610331565b811461039a57600080fd5b5056fea2646970667358221220208a29341d498e1ad7d868833f0f0d5a89b61329c8a7807770f175cb62767cdc64736f6c63430008070033");

    private static final Address CONTRACT_ADDRESS = Address.fromHexString("0x00000000000000000000000000000000000004e2");

    private static final Address SENDER_ADDRESS = Address.fromHexString("0x00000000000000000000000000000000000004e6");

    private static final Address RECEIVER_ADDRESS = Address.fromHexString("0x00000000000000000000000000000000000004e5");

    private final ContractCallService contractCallService;

    @Test
    void pureCall() {
        //func x(uint n) public pure returns (uint)
        final var pureFuncHash = "0x1079023a000000000000000000000000000000000000000000000000000000000000007b";
        final var successfulReadResponse = "0x000000000000000000000000000000000000000000000000000000000000007d";
        final var serviceParameters = callBody(pureFuncHash, 0);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);
    }

    @Test
    void viewCall() {
        //func x(uint n) public view returns (uint)
        final var viewFuncHash = "0x1079023a0000000000000000000000000000000000000000000000000000000000000156";
        final var successfulReadResponse = "0x0000000000000000000000000000000000000000000000000000000000000158";
        final var serviceParameters = callBody(viewFuncHash, 0);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);
    }

    @Test
    void callPayable() {
        final var payableFunc = "0x289ec69b";
        final var params = callBody(payableFunc, 9000L);

        persistEntities(false);

        final var isSuccessful = contractCallService.processCall(params);
        assertThat(isSuccessful).isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    void transferFunds() {
        final var serviceParameters = callBody("0x", 7L);
        persistEntities(true);

        assertThatCode(() -> contractCallService.processCall(serviceParameters)).doesNotThrowAnyException();
    }

    @Test
    void balanceCall() {
        final var balanceCall = "0xc32b6ba100000000000000000000000000000000000000000000000000000000000004e2";
        final var expectedBalance = "0x00000000000000000000000000000000000000000000000000000000000005dc";
        final var params = callBody(balanceCall, 0);

        persistEntities(false);

        final var isSuccessful = contractCallService.processCall(params);
        assertThat(isSuccessful).isEqualTo(expectedBalance);
    }

    @Test
    void invalidFunctionSig() {
        final var wrongFunctionSignature = "0x542ec32e";
        final var serviceParameters = callBody(wrongFunctionSignature, 0);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).isInstanceOf(InvalidTransactionException.class)
                .hasMessage("CONTRACT_REVERT_EXECUTED");
    }

    @Test
    void transferNegative() {
        final var serviceParameters = callBody("0x", -5L);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void transferExceedsBalance() {
        final var serviceParameters = callBody("0x", 210000L);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).isInstanceOf(InvalidTransactionException.class);
    }

    private CallServiceParameters callBody(String callData, long value) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        final var data = Bytes.fromHexString(callData);
        final var receiver = callData.equals("0x") ? RECEIVER_ADDRESS : CONTRACT_ADDRESS;

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(receiver)
                .callData(data)
                .providedGasLimit(120000000L)
                .isStatic(true)
                .build();
    }

    private void persistEntities(boolean isRegularTransfer) {

        if (isRegularTransfer) {
            final var receiverEntityId = fromEvmAddress(RECEIVER_ADDRESS.toArrayUnsafe());
            final var receiverEvmAddress = toEvmAddress(receiverEntityId);

            domainBuilder.entity().customize(e ->
                            e.id(receiverEntityId.getId())
                                    .num(receiverEntityId.getEntityNum())
                                    .evmAddress(receiverEvmAddress))
                    .persist();
        }
        final var ContractEntityId = fromEvmAddress(CONTRACT_ADDRESS.toArrayUnsafe());
        final var ContractEvmAddress = toEvmAddress(ContractEntityId);

        domainBuilder.entity().customize(e ->
                e.id(ContractEntityId.getId())
                        .num(ContractEntityId.getEntityNum())
                        .evmAddress(ContractEvmAddress)
                        .type(CONTRACT).balance(1500L)).persist();

        domainBuilder.contract().customize(c ->
                        c.id(ContractEntityId.getId())
                                .runtimeBytecode(RUNTIME_BYTES.toArrayUnsafe()))
                .persist();

        domainBuilder.recordFile().customize(f ->
                f.bytes(RUNTIME_BYTES.toArrayUnsafe())).persist();

        final var SenderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());
        final var SenderEvmAddress = toEvmAddress(SenderEntityId);

        domainBuilder.entity().customize(e ->
                        e.id(SenderEntityId.getId())
                                .num(SenderEntityId.getEntityNum())
                                .evmAddress(SenderEvmAddress)
                                .balance(20000L))
                .persist();
    }
}
