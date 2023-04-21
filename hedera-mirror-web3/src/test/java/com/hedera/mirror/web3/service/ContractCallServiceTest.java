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

package com.hedera.mirror.web3.service;

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

import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.LongFunction;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractCallServiceTest extends Web3IntegrationTest {
    private static final Bytes ETH_CALL_CONTRACT_BYTES = Bytes.fromHexString(
            "60806040526004361061007b5760003560e01c806381a73ad51161004e57806381a73ad51461014157806393423e9c1461017e5780639ac27b62146101bb578063a26388bb146101eb5761007b565b80636601c296146100805780636f0fccab146100bd5780638070450f146100fa57806380b9f03c14610125575b600080fd5b34801561008c57600080fd5b506100a760048036038101906100a291906105e8565b610202565b6040516100b491906106b0565b60405180910390f35b3480156100c957600080fd5b506100e460048036038101906100df9190610730565b610241565b6040516100f191906106b0565b60405180910390f35b34801561010657600080fd5b5061010f6102be565b60405161011c9190610776565b60405180910390f35b61013f600480360381019061013a91906107cf565b6102c7565b005b34801561014d57600080fd5b5061016860048036038101906101639190610730565b610311565b60405161017591906106b0565b60405180910390f35b34801561018a57600080fd5b506101a560048036038101906101a09190610730565b61038e565b6040516101b29190610776565b60405180910390f35b6101d560048036038101906101d091906105e8565b6103af565b6040516101e291906106b0565b60405180910390f35b3480156101f757600080fd5b50610200610453565b005b60606040518060400160405280600481526020017f74657374000000000000000000000000000000000000000000000000000000008152509050919050565b60608173ffffffffffffffffffffffffffffffffffffffff166306fdde036040518163ffffffff1660e01b8152600401600060405180830381865afa15801561028e573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906102b7919061086c565b9050919050565b60006004905090565b8073ffffffffffffffffffffffffffffffffffffffff166108fc349081150290604051600060405180830381858888f1935050505015801561030d573d6000803e3d6000fd5b5050565b60608173ffffffffffffffffffffffffffffffffffffffff166395d89b416040518163ffffffff1660e01b8152600401600060405180830381865afa15801561035e573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f82011682018060405250810190610387919061086c565b9050919050565b60008173ffffffffffffffffffffffffffffffffffffffff16319050919050565b606081600090816103c09190610ac1565b50600080546103ce906108e4565b80601f01602080910402602001604051908101604052809291908181526020018280546103fa906108e4565b80156104475780601f1061041c57610100808354040283529160200191610447565b820191906000526020600020905b81548152906001019060200180831161042a57829003601f168201915b50505050509050919050565b6040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161048590610bdf565b60405180910390fd5b6000604051905090565b600080fd5b600080fd5b600080fd5b600080fd5b6000601f19601f8301169050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b6104f5826104ac565b810181811067ffffffffffffffff82111715610514576105136104bd565b5b80604052505050565b600061052761048e565b905061053382826104ec565b919050565b600067ffffffffffffffff821115610553576105526104bd565b5b61055c826104ac565b9050602081019050919050565b82818337600083830152505050565b600061058b61058684610538565b61051d565b9050828152602081018484840111156105a7576105a66104a7565b5b6105b2848285610569565b509392505050565b600082601f8301126105cf576105ce6104a2565b5b81356105df848260208601610578565b91505092915050565b6000602082840312156105fe576105fd610498565b5b600082013567ffffffffffffffff81111561061c5761061b61049d565b5b610628848285016105ba565b91505092915050565b600081519050919050565b600082825260208201905092915050565b60005b8381101561066b578082015181840152602081019050610650565b60008484015250505050565b600061068282610631565b61068c818561063c565b935061069c81856020860161064d565b6106a5816104ac565b840191505092915050565b600060208201905081810360008301526106ca8184610677565b905092915050565b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b60006106fd826106d2565b9050919050565b61070d816106f2565b811461071857600080fd5b50565b60008135905061072a81610704565b92915050565b60006020828403121561074657610745610498565b5b60006107548482850161071b565b91505092915050565b6000819050919050565b6107708161075d565b82525050565b600060208201905061078b6000830184610767565b92915050565b600061079c826106d2565b9050919050565b6107ac81610791565b81146107b757600080fd5b50565b6000813590506107c9816107a3565b92915050565b6000602082840312156107e5576107e4610498565b5b60006107f3848285016107ba565b91505092915050565b600061080f61080a84610538565b61051d565b90508281526020810184848401111561082b5761082a6104a7565b5b61083684828561064d565b509392505050565b600082601f830112610853576108526104a2565b5b81516108638482602086016107fc565b91505092915050565b60006020828403121561088257610881610498565b5b600082015167ffffffffffffffff8111156108a05761089f61049d565b5b6108ac8482850161083e565b91505092915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b600060028204905060018216806108fc57607f821691505b60208210810361090f5761090e6108b5565b5b50919050565b60008190508160005260206000209050919050565b60006020601f8301049050919050565b600082821b905092915050565b6000600883026109777fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8261093a565b610981868361093a565b95508019841693508086168417925050509392505050565b6000819050919050565b60006109be6109b96109b48461075d565b610999565b61075d565b9050919050565b6000819050919050565b6109d8836109a3565b6109ec6109e4826109c5565b848454610947565b825550505050565b600090565b610a016109f4565b610a0c8184846109cf565b505050565b5b81811015610a3057610a256000826109f9565b600181019050610a12565b5050565b601f821115610a7557610a4681610915565b610a4f8461092a565b81016020851015610a5e578190505b610a72610a6a8561092a565b830182610a11565b50505b505050565b600082821c905092915050565b6000610a9860001984600802610a7a565b1980831691505092915050565b6000610ab18383610a87565b9150826002028217905092915050565b610aca82610631565b67ffffffffffffffff811115610ae357610ae26104bd565b5b610aed82546108e4565b610af8828285610a34565b600060209050601f831160018114610b2b5760008415610b19578287015190505b610b238582610aa5565b865550610b8b565b601f198416610b3986610915565b60005b82811015610b6157848901518255600182019150602085019450602081019050610b3c565b86831015610b7e5784890151610b7a601f891682610a87565b8355505b6001600288020188555050505b505050505050565b7f437573746f6d20726576657274206d6573736167650000000000000000000000600082015250565b6000610bc960158361063c565b9150610bd482610b93565b602082019050919050565b60006020820190508181036000830152610bf881610bbc565b905091905056fea26469706673582212209451fa92c45dc89ee7dbbe6a21bb4071ad0661acd4d3490084998b2b91c680d664736f6c63430008110033");

    private static final Bytes REVERTER_CONTRACT_BYTES = Bytes.fromHexString(
            "6080604052600436106100c25760003560e01c806386451c2b1161007f578063b1c5ae5111610059578063b1c5ae5114610196578063b2e0100c146101ad578063d0efd7ef146101c4578063fe0a3dd7146101ce576100c2565b806386451c2b146101515780638b1533711461016857806390e9b8751461017f576100c2565b80630323d234146100c75780632dac842f146100de57806333fe3fbd146100f5578063353146941461010c57806346fc4bb114610123578063838890561461013a575b600080fd5b3480156100d357600080fd5b506100dc6101e5565b005b3480156100ea57600080fd5b506100f3610228565b005b34801561010157600080fd5b5061010a61022d565b005b34801561011857600080fd5b50610121610249565b005b34801561012f57600080fd5b5061013861027b565b005b34801561014657600080fd5b5061014f6102ad565b005b34801561015d57600080fd5b506101666102c9565b005b34801561017457600080fd5b5061017d610307565b005b34801561018b57600080fd5b5061019461034a565b005b3480156101a257600080fd5b506101ab610385565b005b3480156101b957600080fd5b506101c26103c3565b005b6101cc6103fe565b005b3480156101da57600080fd5b506101e3610439565b005b6000610226576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161021d9061049b565b60405180910390fd5b565b600080fd5b600060649050600080818361024291906104f4565b9050505050565b6040517f0bd3d39c00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b6040517f0bd3d39c00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b60006064905060008081836102c291906104f4565b9050505050565b60016040517fcc4263a00000000000000000000000000000000000000000000000000000000081526004016102fe919061056a565b60405180910390fd5b6000610348576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161033f9061049b565b60405180910390fd5b565b6040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161037c906105e4565b60405180910390fd5b60016040517fcc4263a00000000000000000000000000000000000000000000000000000000081526004016103ba919061056a565b60405180910390fd5b6040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016103f590610650565b60405180910390fd5b6040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610430906106bc565b60405180910390fd5b600080fd5b600082825260208201905092915050565b7f536f6d6520726576657274206d65737361676500000000000000000000000000600082015250565b600061048560138361043e565b91506104908261044f565b602082019050919050565b600060208201905081810360008301526104b481610478565b9050919050565b6000819050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601260045260246000fd5b60006104ff826104bb565b915061050a836104bb565b92508261051a576105196104c5565b5b828204905092915050565b6000819050919050565b6000819050919050565b600061055461054f61054a84610525565b61052f565b6104bb565b9050919050565b61056481610539565b82525050565b6000604082019050818103600083015261058381610478565b9050610592602083018461055b565b92915050565b7f526576657274526561736f6e5669657700000000000000000000000000000000600082015250565b60006105ce60108361043e565b91506105d982610598565b602082019050919050565b600060208201905081810360008301526105fd816105c1565b9050919050565b7f526576657274526561736f6e5075726500000000000000000000000000000000600082015250565b600061063a60108361043e565b915061064582610604565b602082019050919050565b600060208201905081810360008301526106698161062d565b9050919050565b7f526576657274526561736f6e50617961626c6500000000000000000000000000600082015250565b60006106a660138361043e565b91506106b182610670565b602082019050919050565b600060208201905081810360008301526106d581610699565b905091905056fea2646970667358221220b3abdc0b435d0a919227a379786c6bba37b96507b92d2da1074dea22ce02d1d064736f6c63430008120033");

    private static final Address REVERTER_CONTRACT_ADDRESS =
            Address.fromHexString("0x00000000000000000000000000000000000004e1");

    private static final Address ETH_CALL_CONTRACT_ADDRESS =
            Address.fromHexString("0x00000000000000000000000000000000000004e9");

    private static final Address SENDER_ADDRESS = Address.fromHexString("0x00000000000000000000000000000000000003e6");

    private static final Address RECEIVER_ADDRESS = Address.fromHexString("0x00000000000000000000000000000000000003e5");

    private static final Address TOKEN_ADDRESS = Address.fromHexString("0x00000000000000000000000000000000000003e4");

    private static final String GAS_METRICS = "hedera.mirror.web3.call.gas";
    private static final LongFunction<String> hexValueOf =
            value -> Bytes.ofUnsignedLong(value).toShortHexString().replace("0x", "");

    private final MeterRegistry meterRegistry;
    private final ContractCallService contractCallService;

    @Test
    void pureCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // multiplySimpleNumbers()
        final var pureFuncHash = "8070450f";
        final var successfulReadResponse = "0x0000000000000000000000000000000000000000000000000000000000000004";
        final var serviceParameters = serviceParameters(pureFuncHash, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForPureCall() {
        final var pureFuncHash = "8070450f";
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var expectedGasUsed = 22294L;
        final var serviceParameters =
                serviceParameters(pureFuncHash, 0, ETH_ESTIMATE_GAS, true, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(hexValueOf.apply(expectedGasUsed));

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void viewCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // returnStorageData()
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var successfulReadResponse =
                "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000047465737400000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(viewFuncHash, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForViewCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var expectedGasUsed = 23447L;
        final var serviceParameters =
                serviceParameters(viewFuncHash, 0, ETH_ESTIMATE_GAS, true, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(hexValueOf.apply(expectedGasUsed));

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void transferFunds() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var serviceParameters = serviceParameters("0x", 7L, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS);
        persistEntities(true);

        assertThatCode(() -> contractCallService.processCall(serviceParameters)).doesNotThrowAnyException();

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // getAccountBalance(address)
        final var balanceCall = "0x93423e9c00000000000000000000000000000000000000000000000000000000000003e6";
        final var expectedBalance = "0x0000000000000000000000000000000000000000000000000000000000004e20";
        final var params = serviceParameters(balanceCall, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        final var isSuccessful = contractCallService.processCall(params);
        assertThat(isSuccessful).isEqualTo(expectedBalance);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForBalanceCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var balanceCall = "0x93423e9c00000000000000000000000000000000000000000000000000000000000003e6";
        final var expectedGasUsed = 22691L;
        final var params = serviceParameters(balanceCall, 0, ETH_ESTIMATE_GAS, true, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        final var isSuccessful = contractCallService.processCall(params);
        assertThat(isSuccessful).isEqualTo(hexValueOf.apply(expectedGasUsed));

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void testRevertDetailMessage() {
        final var revertFunctionSignature = "0xa26388bb";
        final var serviceParameters =
                serviceParameters(revertFunctionSignature, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "Custom revert message")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000");
    }

    @ParameterizedTest
    @EnumSource(RevertFunctions.class)
    void testReverts(final RevertFunctions revertFunctions) {
        final var serviceParameters =
                serviceParameters(revertFunctions.functionSignature, 0, ETH_CALL, true, REVERTER_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", revertFunctions.errorDetail)
                .hasFieldOrPropertyWithValue("data", revertFunctions.errorData);
    }

    @Test
    void invalidFunctionSig() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        final var wrongFunctionSignature = "0x542ec32e";
        final var serviceParameters =
                serviceParameters(wrongFunctionSignature, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("CONTRACT_REVERT_EXECUTED")
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void transferNegative() {
        final var serviceParameters = serviceParameters("0x", -5L, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void transferExceedsBalance() {
        final var serviceParameters = serviceParameters("0x", 210000L, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    /**
     * _to.transfer(msg.value) fails due to the static frame,{@link CallOperation} this will be
     * supported with future release with gas_estimate support.
     */
    @Test
    void transferThruContract() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        // transferHbarsToAddress(address)
        final var stateChangePayable = "0x80b9f03c00000000000000000000000000000000000000000000000000000000000004e6";
        final var params = serviceParameters(stateChangePayable, 90L, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(params))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage(LOCAL_CALL_MODIFICATION_EXCEPTION.toString())
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void estimateGasForStateChangeCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var stateChangeHash =
                "0x9ac27b62000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters =
                serviceParameters(stateChangeHash, 0, ETH_ESTIMATE_GAS, false, ETH_CALL_CONTRACT_ADDRESS);
        final var expectedGasUsed = 29780;

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(hexValueOf.apply(expectedGasUsed));

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void stateChangeFails() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);

        // writeToStorageSlot(string)
        final var stateChangeHash =
                "0x9ac27b62000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(stateChangeHash, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    private CallServiceParameters serviceParameters(
            String callData, long value, CallType callType, boolean isStatic, Address contract) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        final var data = Bytes.fromHexString(callData);
        final var receiver = callData.equals("0x") ? RECEIVER_ADDRESS : contract;

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(receiver)
                .callData(data)
                .gas(120000L)
                .isEstimate(callType == ETH_ESTIMATE_GAS)
                .isStatic(isStatic)
                .callType(callType)
                .build();
    }

    private double getGasUsedBeforeExecution(final CallType callType) {
        final var callCounter = meterRegistry.find(GAS_METRICS).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst();

        var gasUsedBeforeExecution = 0d;
        if (callCounter.isPresent()) {
            gasUsedBeforeExecution = callCounter.get().count();
        }

        return gasUsedBeforeExecution;
    }

    private void assertGasUsedIsPositive(final double gasUsedBeforeExecution, final CallType callType) {
        final var afterExecution = meterRegistry.find(GAS_METRICS).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        final var gasConsumed = afterExecution.count() - gasUsedBeforeExecution;
        assertThat(gasConsumed).isPositive();
    }

    private void persistEntities(boolean isRegularTransfer) {

        if (isRegularTransfer) {
            final var receiverEntityId = fromEvmAddress(RECEIVER_ADDRESS.toArrayUnsafe());
            final var receiverEvmAddress = toEvmAddress(receiverEntityId);

            domainBuilder
                    .entity()
                    .customize(e -> e.id(receiverEntityId.getId())
                            .num(receiverEntityId.getEntityNum())
                            .evmAddress(receiverEvmAddress)
                            .type(CONTRACT))
                    .persist();
        }
        final var contractEntityId = fromEvmAddress(ETH_CALL_CONTRACT_ADDRESS.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(contractEntityId.getId())
                        .num(contractEntityId.getEntityNum())
                        .evmAddress(contractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(contractEntityId.getId()).runtimeBytecode(ETH_CALL_CONTRACT_BYTES.toArrayUnsafe()))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(contractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(ETH_CALL_CONTRACT_BYTES.toArrayUnsafe()))
                .persist();

        final var revertContractEntityId = fromEvmAddress(REVERTER_CONTRACT_ADDRESS.toArrayUnsafe());
        final var revertContractEvmAddress = toEvmAddress(revertContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(revertContractEntityId.getId())
                        .num(revertContractEntityId.getEntityNum())
                        .evmAddress(revertContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c ->
                        c.id(revertContractEntityId.getId()).runtimeBytecode(REVERTER_CONTRACT_BYTES.toArrayUnsafe()))
                .persist();

        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());
        final var senderEvmAddress = toEvmAddress(senderEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getEntityNum())
                        .evmAddress(senderEvmAddress)
                        .balance(20000L))
                .persist();

        final var tokenEntityId = fromEvmAddress(TOKEN_ADDRESS.toArrayUnsafe());
        final var tokenEvmAddress = toEvmAddress(tokenEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(tokenEntityId.getId())
                        .num(tokenEntityId.getEntityNum())
                        .evmAddress(tokenEvmAddress)
                        .type(TOKEN)
                        .balance(1500L))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(new TokenId(tokenEntityId)).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();
    }

    @RequiredArgsConstructor
    private enum RevertFunctions {
        REVERT_WITH_CUSTOM_ERROR_PURE("revertWithCustomErrorPure", "35314694", "", "0x0bd3d39c"),
        REVERT_WITH_PANIC_PURE(
                "revertWithPanicPure",
                "83889056",
                "",
                "0x4e487b710000000000000000000000000000000000000000000000000000000000000012"),
        REVERT_PAYABLE(
                "revertPayable",
                "d0efd7ef",
                "RevertReasonPayable",
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013526576657274526561736f6e50617961626c6500000000000000000000000000"),
        REVERT_PURE(
                "revertPure",
                "b2e0100c",
                "RevertReasonPure",
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000010526576657274526561736f6e5075726500000000000000000000000000000000"),
        REVERT_VIEW(
                "revertView",
                "90e9b875",
                "RevertReasonView",
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000010526576657274526561736f6e5669657700000000000000000000000000000000"),
        REVERT_WITH_CUSTOM_ERROR("revertWithCustomError", "46fc4bb1", "", "0x0bd3d39c"),
        REVERT_WITH_NOTHING("revertWithNothing", "fe0a3dd7", "", "0x"),
        REVERT_WITH_NOTHING_PURE("revertWithNothingPure", "2dac842f", "", "0x"),
        REVERT_WITH_PANIC(
                "revertWithPanic",
                "33fe3fbd",
                "",
                "0x4e487b710000000000000000000000000000000000000000000000000000000000000012"),
        REVERT_WITH_STRING(
                "revertWithString",
                "0323d234",
                "Some revert message",
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000"),
        REVERT_WITH_STRING_PURE(
                "revertWithStringPure",
                "8b153371",
                "Some revert message",
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000"),
        REVERT_WITH_CUSTOM_ERROR_WITH_PARAMETERS(
                "revertWithCustomErrorWithParameters",
                "86451c2b",
                "",
                "0xcc4263a0000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000"),
        REVERT_WITH_CUSTOM_ERROR_WITH_PARAMETERS_PURE(
                "revertWithCustomErrorWithParameters",
                "b1c5ae51",
                "",
                "0xcc4263a0000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000");

        private final String name;
        private final String functionSignature;
        private final String errorDetail;
        private final String errorData;
    }
}
