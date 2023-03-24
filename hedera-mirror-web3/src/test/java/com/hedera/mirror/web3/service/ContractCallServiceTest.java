package com.hedera.mirror.web3.service;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractCallServiceTest extends Web3IntegrationTest {
    private static final Bytes CONTRACT_BYTES = Bytes.fromHexString(
            "60806040526004361061007b5760003560e01c806381a73ad51161004e57806381a73ad51461014157806393423e9c1461017e5780639ac27b62146101bb578063a26388bb146101eb5761007b565b80636601c296146100805780636f0fccab146100bd5780638070450f146100fa57806380b9f03c14610125575b600080fd5b34801561008c57600080fd5b506100a760048036038101906100a291906105e8565b610202565b6040516100b491906106b0565b60405180910390f35b3480156100c957600080fd5b506100e460048036038101906100df9190610730565b610241565b6040516100f191906106b0565b60405180910390f35b34801561010657600080fd5b5061010f6102be565b60405161011c9190610776565b60405180910390f35b61013f600480360381019061013a91906107cf565b6102c7565b005b34801561014d57600080fd5b5061016860048036038101906101639190610730565b610311565b60405161017591906106b0565b60405180910390f35b34801561018a57600080fd5b506101a560048036038101906101a09190610730565b61038e565b6040516101b29190610776565b60405180910390f35b6101d560048036038101906101d091906105e8565b6103af565b6040516101e291906106b0565b60405180910390f35b3480156101f757600080fd5b50610200610453565b005b60606040518060400160405280600481526020017f74657374000000000000000000000000000000000000000000000000000000008152509050919050565b60608173ffffffffffffffffffffffffffffffffffffffff166306fdde036040518163ffffffff1660e01b8152600401600060405180830381865afa15801561028e573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906102b7919061086c565b9050919050565b60006004905090565b8073ffffffffffffffffffffffffffffffffffffffff166108fc349081150290604051600060405180830381858888f1935050505015801561030d573d6000803e3d6000fd5b5050565b60608173ffffffffffffffffffffffffffffffffffffffff166395d89b416040518163ffffffff1660e01b8152600401600060405180830381865afa15801561035e573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f82011682018060405250810190610387919061086c565b9050919050565b60008173ffffffffffffffffffffffffffffffffffffffff16319050919050565b606081600090816103c09190610ac1565b50600080546103ce906108e4565b80601f01602080910402602001604051908101604052809291908181526020018280546103fa906108e4565b80156104475780601f1061041c57610100808354040283529160200191610447565b820191906000526020600020905b81548152906001019060200180831161042a57829003601f168201915b50505050509050919050565b6040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161048590610bdf565b60405180910390fd5b6000604051905090565b600080fd5b600080fd5b600080fd5b600080fd5b6000601f19601f8301169050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b6104f5826104ac565b810181811067ffffffffffffffff82111715610514576105136104bd565b5b80604052505050565b600061052761048e565b905061053382826104ec565b919050565b600067ffffffffffffffff821115610553576105526104bd565b5b61055c826104ac565b9050602081019050919050565b82818337600083830152505050565b600061058b61058684610538565b61051d565b9050828152602081018484840111156105a7576105a66104a7565b5b6105b2848285610569565b509392505050565b600082601f8301126105cf576105ce6104a2565b5b81356105df848260208601610578565b91505092915050565b6000602082840312156105fe576105fd610498565b5b600082013567ffffffffffffffff81111561061c5761061b61049d565b5b610628848285016105ba565b91505092915050565b600081519050919050565b600082825260208201905092915050565b60005b8381101561066b578082015181840152602081019050610650565b60008484015250505050565b600061068282610631565b61068c818561063c565b935061069c81856020860161064d565b6106a5816104ac565b840191505092915050565b600060208201905081810360008301526106ca8184610677565b905092915050565b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b60006106fd826106d2565b9050919050565b61070d816106f2565b811461071857600080fd5b50565b60008135905061072a81610704565b92915050565b60006020828403121561074657610745610498565b5b60006107548482850161071b565b91505092915050565b6000819050919050565b6107708161075d565b82525050565b600060208201905061078b6000830184610767565b92915050565b600061079c826106d2565b9050919050565b6107ac81610791565b81146107b757600080fd5b50565b6000813590506107c9816107a3565b92915050565b6000602082840312156107e5576107e4610498565b5b60006107f3848285016107ba565b91505092915050565b600061080f61080a84610538565b61051d565b90508281526020810184848401111561082b5761082a6104a7565b5b61083684828561064d565b509392505050565b600082601f830112610853576108526104a2565b5b81516108638482602086016107fc565b91505092915050565b60006020828403121561088257610881610498565b5b600082015167ffffffffffffffff8111156108a05761089f61049d565b5b6108ac8482850161083e565b91505092915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b600060028204905060018216806108fc57607f821691505b60208210810361090f5761090e6108b5565b5b50919050565b60008190508160005260206000209050919050565b60006020601f8301049050919050565b600082821b905092915050565b6000600883026109777fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8261093a565b610981868361093a565b95508019841693508086168417925050509392505050565b6000819050919050565b60006109be6109b96109b48461075d565b610999565b61075d565b9050919050565b6000819050919050565b6109d8836109a3565b6109ec6109e4826109c5565b848454610947565b825550505050565b600090565b610a016109f4565b610a0c8184846109cf565b505050565b5b81811015610a3057610a256000826109f9565b600181019050610a12565b5050565b601f821115610a7557610a4681610915565b610a4f8461092a565b81016020851015610a5e578190505b610a72610a6a8561092a565b830182610a11565b50505b505050565b600082821c905092915050565b6000610a9860001984600802610a7a565b1980831691505092915050565b6000610ab18383610a87565b9150826002028217905092915050565b610aca82610631565b67ffffffffffffffff811115610ae357610ae26104bd565b5b610aed82546108e4565b610af8828285610a34565b600060209050601f831160018114610b2b5760008415610b19578287015190505b610b238582610aa5565b865550610b8b565b601f198416610b3986610915565b60005b82811015610b6157848901518255600182019150602085019450602081019050610b3c565b86831015610b7e5784890151610b7a601f891682610a87565b8355505b6001600288020188555050505b505050505050565b7f437573746f6d20726576657274206d6573736167650000000000000000000000600082015250565b6000610bc960158361063c565b9150610bd482610b93565b602082019050919050565b60006020820190508181036000830152610bf881610bbc565b905091905056fea26469706673582212209451fa92c45dc89ee7dbbe6a21bb4071ad0661acd4d3490084998b2b91c680d664736f6c63430008110033");

    private static final Address CONTRACT_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e7");

    private static final Address SENDER_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e6");

    private static final Address RECEIVER_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e5");

    private static final Address TOKEN_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e4");

    private static final String GAS_METRICS = "hedera.mirror.web3.call.gas";

    private final MeterRegistry meterRegistry;
    private final ContractCallService contractCallService;

    @Test
    void pureCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        //multiplySimpleNumbers()
        final var pureFuncHash = "8070450f";
        final var successfulReadResponse =
                "0x0000000000000000000000000000000000000000000000000000000000000004";
        final var serviceParameters = serviceParameters(pureFuncHash, 0, ETH_CALL, true);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void viewCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        //returnStorageData()
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var successfulReadResponse =
                "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000047465737400000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(viewFuncHash, 0, ETH_CALL, true);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }


    @Test
    void transferFunds() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var serviceParameters = serviceParameters("0x", 7L, ETH_CALL, true);
        persistEntities(true);

        assertThatCode(() -> contractCallService.processCall(serviceParameters)).doesNotThrowAnyException();

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        //getAccountBalance(address)
        final var balanceCall =
                "0x93423e9c00000000000000000000000000000000000000000000000000000000000004e6";
        final var expectedBalance =
                "0x0000000000000000000000000000000000000000000000000000000000004e20";
        final var params = serviceParameters(balanceCall, 0, ETH_CALL, true);

        persistEntities(false);

        final var isSuccessful = contractCallService.processCall(params);
        assertThat(isSuccessful).isEqualTo(expectedBalance);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void precompileCallReverts() {
        final var tokenNameCall = "0x6f0fccab00000000000000000000000000000000000000000000000000000000000004e4";
        final var serviceParameters = serviceParameters(tokenNameCall, 0, ETH_CALL, false);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).
                isInstanceOf(UnsupportedOperationException.class).hasMessage("Precompile not supported");
    }

    @Test
    void testRevertDetailMessage() {
        final var revertFunctionSignature = "0xa26388bb";
        final var serviceParameters = serviceParameters(revertFunctionSignature, 0, ETH_CALL, true);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).
                isInstanceOf(InvalidTransactionException.class).hasMessage(CONTRACT_REVERT_EXECUTED.name()).hasFieldOrPropertyWithValue("data", "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000");
    }

    @Test
    void invalidFunctionSig() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        final var wrongFunctionSignature = "0x542ec32e";
        final var serviceParameters = serviceParameters(wrongFunctionSignature, 0, ETH_CALL, true);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).isInstanceOf(InvalidTransactionException.class)
                .hasMessage("CONTRACT_REVERT_EXECUTED").hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void transferNegative() {
        final var serviceParameters = serviceParameters("0x", -5L, ETH_CALL, true);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void transferExceedsBalance() {
        final var serviceParameters = serviceParameters("0x", 210000L, ETH_CALL, true);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).isInstanceOf(InvalidTransactionException.class);
    }

    /**
     * _to.transfer(msg.value) fails due to the static frame,{@link CallOperation} this will be
     * supported with future release with gas_estimate support.
     */
    @Test
    void transferThruContract() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        //transferHbarsToAddress(address)
        final var stateChangePayable =
                "0x80b9f03c00000000000000000000000000000000000000000000000000000000000004e6";
        final var params = serviceParameters(stateChangePayable, 90L, ETH_CALL, true);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(params)).
                isInstanceOf(InvalidTransactionException.class)
                .hasMessage(LOCAL_CALL_MODIFICATION_EXCEPTION.toString())
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void stateChangeFails() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        //writeToStorageSlot(string)
        final var stateChangeHash =
                "0x9ac27b62000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(stateChangeHash, 0, ETH_CALL, true);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).
                isInstanceOf(InvalidTransactionException.class).hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void estimateGasSimpleCase() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);

        //multiplySimpleNumbers()
        final var pureFuncHash = "8070450f";
        final var successfulReadResponse =
                "0x0000000000000000000000000000000000000000000000000000000000000004";
        final var serviceParameters = serviceParameters(pureFuncHash, 0, ETH_ESTIMATE_GAS, false);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    private CallServiceParameters serviceParameters(String callData, long value, CallType callType, boolean isStatic) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        final var data = Bytes.fromHexString(callData);
        final var receiver = callData.equals("0x") ? RECEIVER_ADDRESS : CONTRACT_ADDRESS;

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(receiver)
                .callData(data)
                .providedGasLimit(120000L)
                .isStatic(isStatic)
                .callType(callType)
                .build();
    }

    private double getGasUsedBeforeExecution(final CallType callType) {
        final var callCounter = meterRegistry.find(GAS_METRICS).counters()
                .stream().filter(c -> callType.name().equals(c.getId().getTag("type"))).findFirst();

        var gasUsedBeforeExecution = 0d;
        if(callCounter.isPresent()) {
            gasUsedBeforeExecution = callCounter.get().count();
        }

        return gasUsedBeforeExecution;
    }

    private void assertGasUsedIsPositive(final double gasUsedBeforeExecution, final CallType callType) {
        final var afterExecution = meterRegistry.find(GAS_METRICS).counters()
                .stream().filter(c -> callType.name().equals(c.getId().getTag("type"))).findFirst().get();

        final var gasConsumed = afterExecution.count() - gasUsedBeforeExecution;
        assertThat(gasConsumed).isPositive();
    }

    private void persistEntities(boolean isRegularTransfer) {

        if (isRegularTransfer) {
            final var receiverEntityId = fromEvmAddress(RECEIVER_ADDRESS.toArrayUnsafe());
            final var receiverEvmAddress = toEvmAddress(receiverEntityId);

            domainBuilder.entity().customize(e ->
                            e.id(receiverEntityId.getId())
                                    .num(receiverEntityId.getEntityNum())
                                    .evmAddress(receiverEvmAddress)
                                    .type(CONTRACT))
                    .persist();
        }
        final var contractEntityId = fromEvmAddress(CONTRACT_ADDRESS.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        domainBuilder.entity().customize(e ->
                e.id(contractEntityId.getId())
                        .num(contractEntityId.getEntityNum())
                        .evmAddress(contractEvmAddress)
                        .type(CONTRACT).balance(1500L)).persist();

        domainBuilder.contract().customize(c ->
                        c.id(contractEntityId.getId())
                                .runtimeBytecode(CONTRACT_BYTES.toArrayUnsafe()))
                .persist();

        domainBuilder.contractState().customize(c ->
                        c.contractId(contractEntityId.getId())
                                .slot(Bytes.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000000").toArrayUnsafe())
                                .value(Bytes.fromHexString(
                                        "0x4746573740000000000000000000000000000000000000000000000000000000").toArrayUnsafe()))
                .persist();

        domainBuilder.recordFile().customize(f ->
                f.bytes(CONTRACT_BYTES.toArrayUnsafe())).persist();

        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());
        final var senderEvmAddress = toEvmAddress(senderEntityId);

        domainBuilder.entity().customize(e ->
                        e.id(senderEntityId.getId())
                                .num(senderEntityId.getEntityNum())
                                .evmAddress(senderEvmAddress)
                                .balance(20000L))
                .persist();

        final var tokenEntityId = fromEvmAddress(TOKEN_ADDRESS.toArrayUnsafe());
        final var tokenEvmAddress = toEvmAddress(tokenEntityId);

        domainBuilder.entity().customize(e ->
                e.id(tokenEntityId.getId())
                        .num(tokenEntityId.getEntityNum())
                        .evmAddress(tokenEvmAddress)
                        .type(TOKEN).balance(1500L)).persist();

        domainBuilder.token().customize(t ->
                        t.tokenId(new TokenId(tokenEntityId))
                                .type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();
    }
}
