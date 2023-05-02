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
            "6080604052600436106100a75760003560e01c806380b9f03c1161006457806380b9f03c146101e057806381a73ad5146101fc57806393423e9c146102395780639ac27b62146102765780639b23d3d9146102a6578063a26388bb146102e3576100a7565b806315dacbea146100ac578063618dc65e146100e95780636601c296146101125780636f0fccab1461014f5780637c93c87e1461018c5780638070450f146101b5575b600080fd5b3480156100b857600080fd5b506100d360048036038101906100ce9190610ab7565b6102fa565b6040516100e09190610b3a565b60405180910390f35b3480156100f557600080fd5b50610110600480360381019061010b9190610c9b565b610418565b005b34801561011e57600080fd5b5061013960048036038101906101349190610d98565b61053f565b6040516101469190610e60565b60405180910390f35b34801561015b57600080fd5b5061017660048036038101906101719190610e82565b61057e565b6040516101839190610e60565b60405180910390f35b34801561019857600080fd5b506101b360048036038101906101ae9190610e82565b6105fb565b005b3480156101c157600080fd5b506101ca610609565b6040516101d79190610ebe565b60405180910390f35b6101fa60048036038101906101f59190610f17565b610612565b005b34801561020857600080fd5b50610223600480360381019061021e9190610e82565b61065c565b6040516102309190610e60565b60405180910390f35b34801561024557600080fd5b50610260600480360381019061025b9190610e82565b6106d9565b60405161026d9190610ebe565b60405180910390f35b610290600480360381019061028b9190610d98565b6106fa565b60405161029d9190610e60565b60405180910390f35b3480156102b257600080fd5b506102cd60048036038101906102c89190610ab7565b61079e565b6040516102da9190610b3a565b60405180910390f35b3480156102ef57600080fd5b506102f86108bc565b005b600080600061016773ffffffffffffffffffffffffffffffffffffffff166315dacbea60e01b888888886040516024016103379493929190610f53565b604051602081830303815290604052907bffffffffffffffffffffffffffffffffffffffffffffffffffffffff19166020820180517bffffffffffffffffffffffffffffffffffffffffffffffffffffffff83818316178352505050506040516103a19190610fdf565b6000604051808303816000865af19150503d80600081146103de576040519150601f19603f3d011682016040523d82523d6000602084013e6103e3565b606091505b5091509150816103f4576015610409565b80806020019051810190610408919061102f565b5b60030b92505050949350505050565b60008061016773ffffffffffffffffffffffffffffffffffffffff1663618dc65e60e01b858560405160240161044f9291906110a6565b604051602081830303815290604052907bffffffffffffffffffffffffffffffffffffffffffffffffffffffff19166020820180517bffffffffffffffffffffffffffffffffffffffffffffffffffffffff83818316178352505050506040516104b99190610fdf565b6000604051808303816000865af19150503d80600081146104f6576040519150601f19603f3d011682016040523d82523d6000602084013e6104fb565b606091505b50915091507f4af4780e06fe8cb9df64b0794fa6f01399af979175bb988e35e0e57e594567bc82826040516105319291906110f1565b60405180910390a150505050565b60606040518060400160405280600481526020017f74657374000000000000000000000000000000000000000000000000000000008152509050919050565b60608173ffffffffffffffffffffffffffffffffffffffff166306fdde036040518163ffffffff1660e01b8152600401600060405180830381865afa1580156105cb573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906105f49190611191565b9050919050565b61060581336108f7565b5050565b60006004905090565b8073ffffffffffffffffffffffffffffffffffffffff166108fc349081150290604051600060405180830381858888f19350505050158015610658573d6000803e3d6000fd5b5050565b60608173ffffffffffffffffffffffffffffffffffffffff166395d89b416040518163ffffffff1660e01b8152600401600060405180830381865afa1580156106a9573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906106d29190611191565b9050919050565b60008173ffffffffffffffffffffffffffffffffffffffff16319050919050565b6060816000908161070b91906113e6565b506000805461071990611209565b80601f016020809104026020016040519081016040528092919081815260200182805461074590611209565b80156107925780601f1061076757610100808354040283529160200191610792565b820191906000526020600020905b81548152906001019060200180831161077557829003601f168201915b50505050509050919050565b600080600061016773ffffffffffffffffffffffffffffffffffffffff16639b23d3d960e01b888888886040516024016107db9493929190610f53565b604051602081830303815290604052907bffffffffffffffffffffffffffffffffffffffffffffffffffffffff19166020820180517bffffffffffffffffffffffffffffffffffffffffffffffffffffffff83818316178352505050506040516108459190610fdf565b6000604051808303816000865af19150503d8060008114610882576040519150601f19603f3d011682016040523d82523d6000602084013e610887565b606091505b5091509150816108985760156108ad565b808060200190518101906108ac919061102f565b5b60030b92505050949350505050565b6040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016108ee90611504565b60405180910390fd5b600080600061016773ffffffffffffffffffffffffffffffffffffffff16635b8f858460e01b8686604051602401610930929190611524565b604051602081830303815290604052907bffffffffffffffffffffffffffffffffffffffffffffffffffffffff19166020820180517bffffffffffffffffffffffffffffffffffffffffffffffffffffffff838183161783525050505060405161099a9190610fdf565b6000604051808303816000865af19150503d80600081146109d7576040519150601f19603f3d011682016040523d82523d6000602084013e6109dc565b606091505b5091509150816109ed576015610a02565b80806020019051810190610a01919061102f565b5b60030b9250505092915050565b6000604051905090565b600080fd5b600080fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b6000610a4e82610a23565b9050919050565b610a5e81610a43565b8114610a6957600080fd5b50565b600081359050610a7b81610a55565b92915050565b6000819050919050565b610a9481610a81565b8114610a9f57600080fd5b50565b600081359050610ab181610a8b565b92915050565b60008060008060808587031215610ad157610ad0610a19565b5b6000610adf87828801610a6c565b9450506020610af087828801610a6c565b9350506040610b0187828801610a6c565b9250506060610b1287828801610aa2565b91505092959194509250565b60008160070b9050919050565b610b3481610b1e565b82525050565b6000602082019050610b4f6000830184610b2b565b92915050565b600080fd5b600080fd5b6000601f19601f8301169050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b610ba882610b5f565b810181811067ffffffffffffffff82111715610bc757610bc6610b70565b5b80604052505050565b6000610bda610a0f565b9050610be68282610b9f565b919050565b600067ffffffffffffffff821115610c0657610c05610b70565b5b610c0f82610b5f565b9050602081019050919050565b82818337600083830152505050565b6000610c3e610c3984610beb565b610bd0565b905082815260208101848484011115610c5a57610c59610b5a565b5b610c65848285610c1c565b509392505050565b600082601f830112610c8257610c81610b55565b5b8135610c92848260208601610c2b565b91505092915050565b60008060408385031215610cb257610cb1610a19565b5b6000610cc085828601610a6c565b925050602083013567ffffffffffffffff811115610ce157610ce0610a1e565b5b610ced85828601610c6d565b9150509250929050565b600067ffffffffffffffff821115610d1257610d11610b70565b5b610d1b82610b5f565b9050602081019050919050565b6000610d3b610d3684610cf7565b610bd0565b905082815260208101848484011115610d5757610d56610b5a565b5b610d62848285610c1c565b509392505050565b600082601f830112610d7f57610d7e610b55565b5b8135610d8f848260208601610d28565b91505092915050565b600060208284031215610dae57610dad610a19565b5b600082013567ffffffffffffffff811115610dcc57610dcb610a1e565b5b610dd884828501610d6a565b91505092915050565b600081519050919050565b600082825260208201905092915050565b60005b83811015610e1b578082015181840152602081019050610e00565b60008484015250505050565b6000610e3282610de1565b610e3c8185610dec565b9350610e4c818560208601610dfd565b610e5581610b5f565b840191505092915050565b60006020820190508181036000830152610e7a8184610e27565b905092915050565b600060208284031215610e9857610e97610a19565b5b6000610ea684828501610a6c565b91505092915050565b610eb881610a81565b82525050565b6000602082019050610ed36000830184610eaf565b92915050565b6000610ee482610a23565b9050919050565b610ef481610ed9565b8114610eff57600080fd5b50565b600081359050610f1181610eeb565b92915050565b600060208284031215610f2d57610f2c610a19565b5b6000610f3b84828501610f02565b91505092915050565b610f4d81610a43565b82525050565b6000608082019050610f686000830187610f44565b610f756020830186610f44565b610f826040830185610f44565b610f8f6060830184610eaf565b95945050505050565b600081519050919050565b600081905092915050565b6000610fb982610f98565b610fc38185610fa3565b9350610fd3818560208601610dfd565b80840191505092915050565b6000610feb8284610fae565b915081905092915050565b60008160030b9050919050565b61100c81610ff6565b811461101757600080fd5b50565b60008151905061102981611003565b92915050565b60006020828403121561104557611044610a19565b5b60006110538482850161101a565b91505092915050565b600082825260208201905092915050565b600061107882610f98565b611082818561105c565b9350611092818560208601610dfd565b61109b81610b5f565b840191505092915050565b60006040820190506110bb6000830185610f44565b81810360208301526110cd818461106d565b90509392505050565b60008115159050919050565b6110eb816110d6565b82525050565b600060408201905061110660008301856110e2565b8181036020830152611118818461106d565b90509392505050565b600061113461112f84610cf7565b610bd0565b9050828152602081018484840111156111505761114f610b5a565b5b61115b848285610dfd565b509392505050565b600082601f83011261117857611177610b55565b5b8151611188848260208601611121565b91505092915050565b6000602082840312156111a7576111a6610a19565b5b600082015167ffffffffffffffff8111156111c5576111c4610a1e565b5b6111d184828501611163565b91505092915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b6000600282049050600182168061122157607f821691505b602082108103611234576112336111da565b5b50919050565b60008190508160005260206000209050919050565b60006020601f8301049050919050565b600082821b905092915050565b60006008830261129c7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8261125f565b6112a6868361125f565b95508019841693508086168417925050509392505050565b6000819050919050565b60006112e36112de6112d984610a81565b6112be565b610a81565b9050919050565b6000819050919050565b6112fd836112c8565b611311611309826112ea565b84845461126c565b825550505050565b600090565b611326611319565b6113318184846112f4565b505050565b5b818110156113555761134a60008261131e565b600181019050611337565b5050565b601f82111561139a5761136b8161123a565b6113748461124f565b81016020851015611383578190505b61139761138f8561124f565b830182611336565b50505b505050565b600082821c905092915050565b60006113bd6000198460080261139f565b1980831691505092915050565b60006113d683836113ac565b9150826002028217905092915050565b6113ef82610de1565b67ffffffffffffffff81111561140857611407610b70565b5b6114128254611209565b61141d828285611359565b600060209050601f831160018114611450576000841561143e578287015190505b61144885826113ca565b8655506114b0565b601f19841661145e8661123a565b60005b8281101561148657848901518255600182019150602085019450602081019050611461565b868310156114a3578489015161149f601f8916826113ac565b8355505b6001600288020188555050505b505050505050565b7f437573746f6d20726576657274206d6573736167650000000000000000000000600082015250565b60006114ee601583610dec565b91506114f9826114b8565b602082019050919050565b6000602082019050818103600083015261151d816114e1565b9050919050565b60006040820190506115396000830185610f44565b6115466020830184610f44565b939250505056fea2646970667358221220e485f06ef9cc8c0d2f521fe02eed8b9feaff7e069312ec3661e16b1bed48b20464736f6c63430008120033");

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
            value -> Bytes.ofUnsignedLong(value).toHexString();

    private final MeterRegistry meterRegistry;
    private final ContractCallService contractCallService;

    @Test
    void pureCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        // multiplySimpleNumbers()
        final var pureFuncHash = "8070450f";
        final var successfulReadResponse = "0x0000000000000000000000000000000000000000000000000000000000000004";
        final var serviceParameters = serviceParameters(pureFuncHash, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS, 0);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForPureCall() {
        final var pureFuncHash = "8070450f";
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var expectedGasUsed = 22217L;
        final var serviceParameters =
                serviceParameters(pureFuncHash, 0, ETH_ESTIMATE_GAS, true, ETH_CALL_CONTRACT_ADDRESS, 0);

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
        final var serviceParameters = serviceParameters(viewFuncHash, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS, 0);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForViewCall() {
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var expectedGasUsed = 23340L;
        final var serviceParameters =
                serviceParameters(viewFuncHash, 0, ETH_ESTIMATE_GAS, true, ETH_CALL_CONTRACT_ADDRESS, 0);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(hexValueOf.apply(expectedGasUsed));
    }

    @Test
    void transferFunds() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var serviceParameters = serviceParameters("0x", 7L, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS, 0);
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
        final var params = serviceParameters(balanceCall, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS, 0);

        persistEntities(false);

        final var isSuccessful = contractCallService.processCall(params);
        assertThat(isSuccessful).isEqualTo(expectedBalance);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForBalanceCall() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_ESTIMATE_GAS);
        final var balanceCall = "0x93423e9c00000000000000000000000000000000000000000000000000000000000003e6";
        final var expectedGasUsed = 22715L;
        final var params =
                serviceParameters(balanceCall, 0, ETH_ESTIMATE_GAS, true, ETH_CALL_CONTRACT_ADDRESS, 15_000_000L);

        persistEntities(false);

        final var estimatedGas = contractCallService.processCall(params);
        assertThat(estimatedGas).isEqualTo(hexValueOf.apply(expectedGasUsed));

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_ESTIMATE_GAS);
    }

    @Test
    void testRevertDetailMessage() {
        final var revertFunctionSignature = "0xa26388bb";
        final var serviceParameters =
                serviceParameters(revertFunctionSignature, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS, 0);

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
                serviceParameters(revertFunctions.functionSignature, 0, ETH_CALL, true, REVERTER_CONTRACT_ADDRESS, 0);

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
                serviceParameters(wrongFunctionSignature, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS, 0);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("CONTRACT_REVERT_EXECUTED")
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void transferNegative() {
        final var serviceParameters = serviceParameters("0x", -5L, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS, 0);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void transferExceedsBalance() {
        final var serviceParameters = serviceParameters("0x", 210000L, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS, 0);
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
        final var params = serviceParameters(stateChangePayable, 90L, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS, 0);

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
                serviceParameters(stateChangeHash, 0, ETH_ESTIMATE_GAS, false, ETH_CALL_CONTRACT_ADDRESS, 0);
        final var expectedGasUsed = 29601L;

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
        final var serviceParameters =
                serviceParameters(stateChangeHash, 0, ETH_CALL, true, ETH_CALL_CONTRACT_ADDRESS, 0);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue("data", "0x");

        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void estimateGasWithExactValue() {
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var expectedAndProvidedGas = 22579L;
        final var serviceParameters = serviceParameters(
                viewFuncHash, 0, ETH_ESTIMATE_GAS, false, ETH_CALL_CONTRACT_ADDRESS, expectedAndProvidedGas);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(hexValueOf.apply(expectedAndProvidedGas));
    }

    @Test
    void ercPrecompileCallRevertsForEstimateGas() {
        final var tokenNameCall = "0x6f0fccab00000000000000000000000000000000000000000000000000000000000003e4";
        final var serviceParameters =
                serviceParameters(tokenNameCall, 0, ETH_ESTIMATE_GAS, false, ETH_CALL_CONTRACT_ADDRESS, 0L);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Precompile not supported for non-static frames");
    }

    @Test
    void precompileCallRevertsForEstimateGas() {
        final var freezeTokenCall = "0x7c93c87e00000000000000000000000000000000000000000000000000000000000003e4";
        final var serviceParameters =
                serviceParameters(freezeTokenCall, 0, ETH_ESTIMATE_GAS, false, ETH_CALL_CONTRACT_ADDRESS, 0L);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Precompile not supported for non-static frames");
    }

    private CallServiceParameters serviceParameters(
            String callData, long value, CallType callType, boolean isStatic, Address contract, long estimatedGas) {
        final var isGasEstimate = callType == ETH_ESTIMATE_GAS;
        final var gas = (isGasEstimate && estimatedGas > 0) ? estimatedGas : 120000L;
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        final var data = Bytes.fromHexString(callData);
        final var receiver = callData.equals("0x") ? RECEIVER_ADDRESS : contract;

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(receiver)
                .callData(data)
                .gas(gas)
                .isEstimate(isGasEstimate)
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
