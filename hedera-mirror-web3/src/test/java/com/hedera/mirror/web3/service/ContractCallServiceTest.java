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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

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
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractCallServiceTest extends Web3IntegrationTest {
    private static final Bytes CONTRACT_BYTES = Bytes.fromHexString(
            "6080604052600436106100705760003560e01c806380b9f03c1161004e57806380b9f03c1461011a57806381a73ad51461013657806393423e9c146101735780639ac27b62146101b057610070565b80636601c296146100755780636f0fccab146100b25780638070450f146100ef575b600080fd5b34801561008157600080fd5b5061009c60048036038101906100979190610636565b6101ed565b6040516100a99190610732565b60405180910390f35b3480156100be57600080fd5b506100d960048036038101906100d491906105dc565b6101f8565b6040516100e69190610710565b60405180910390f35b3480156100fb57600080fd5b50610104610284565b6040516101119190610732565b60405180910390f35b610134600480360381019061012f9190610609565b61028d565b005b34801561014257600080fd5b5061015d600480360381019061015891906105dc565b6102d7565b60405161016a9190610710565b60405180910390f35b34801561017f57600080fd5b5061019a600480360381019061019591906105dc565b610363565b6040516101a79190610732565b60405180910390f35b3480156101bc57600080fd5b506101d760048036038101906101d29190610636565b610384565b6040516101e49190610710565b60405180910390f35b600080549050919050565b60608173ffffffffffffffffffffffffffffffffffffffff166306fdde036040518163ffffffff1660e01b815260040160006040518083038186803b15801561024057600080fd5b505afa158015610254573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f8201168201806040525081019061027d919061067f565b9050919050565b60006004905090565b8073ffffffffffffffffffffffffffffffffffffffff166108fc349081150290604051600060405180830381858888f193505050501580156102d3573d6000803e3d6000fd5b5050565b60608173ffffffffffffffffffffffffffffffffffffffff166395d89b416040518163ffffffff1660e01b815260040160006040518083038186803b15801561031f57600080fd5b505afa158015610333573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f8201168201806040525081019061035c919061067f565b9050919050565b60008173ffffffffffffffffffffffffffffffffffffffff16319050919050565b6060816001908051906020019061039c92919061042f565b50600180546103aa9061084f565b80601f01602080910402602001604051908101604052809291908181526020018280546103d69061084f565b80156104235780601f106103f857610100808354040283529160200191610423565b820191906000526020600020905b81548152906001019060200180831161040657829003601f168201915b50505050509050919050565b82805461043b9061084f565b90600052602060002090601f01602090048101928261045d57600085556104a4565b82601f1061047657805160ff19168380011785556104a4565b828001600101855582156104a4579182015b828111156104a3578251825591602001919060010190610488565b5b5090506104b191906104b5565b5090565b5b808211156104ce5760008160009055506001016104b6565b5090565b60006104e56104e084610772565b61074d565b90508281526020810184848401111561050157610500610915565b5b61050c84828561080d565b509392505050565b600061052761052284610772565b61074d565b90508281526020810184848401111561054357610542610915565b5b61054e84828561081c565b509392505050565b60008135905061056581610935565b92915050565b60008135905061057a8161094c565b92915050565b600082601f83011261059557610594610910565b5b81356105a58482602086016104d2565b91505092915050565b600082601f8301126105c3576105c2610910565b5b81516105d3848260208601610514565b91505092915050565b6000602082840312156105f2576105f161091f565b5b600061060084828501610556565b91505092915050565b60006020828403121561061f5761061e61091f565b5b600061062d8482850161056b565b91505092915050565b60006020828403121561064c5761064b61091f565b5b600082013567ffffffffffffffff81111561066a5761066961091a565b5b61067684828501610580565b91505092915050565b6000602082840312156106955761069461091f565b5b600082015167ffffffffffffffff8111156106b3576106b261091a565b5b6106bf848285016105ae565b91505092915050565b60006106d3826107a3565b6106dd81856107ae565b93506106ed81856020860161081c565b6106f681610924565b840191505092915050565b61070a81610803565b82525050565b6000602082019050818103600083015261072a81846106c8565b905092915050565b60006020820190506107476000830184610701565b92915050565b6000610757610768565b90506107638282610881565b919050565b6000604051905090565b600067ffffffffffffffff82111561078d5761078c6108e1565b5b61079682610924565b9050602081019050919050565b600081519050919050565b600082825260208201905092915050565b60006107ca826107e3565b9050919050565b60006107dc826107e3565b9050919050565b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b6000819050919050565b82818337600083830152505050565b60005b8381101561083a57808201518184015260208101905061081f565b83811115610849576000848401525b50505050565b6000600282049050600182168061086757607f821691505b6020821081141561087b5761087a6108b2565b5b50919050565b61088a82610924565b810181811067ffffffffffffffff821117156108a9576108a86108e1565b5b80604052505050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b600080fd5b600080fd5b600080fd5b600080fd5b6000601f19601f8301169050919050565b61093e816107bf565b811461094957600080fd5b50565b610955816107d1565b811461096057600080fd5b5056fea2646970667358221220b7f5982b5294da8fd70c30853271e8b8101cd7e705958bc9ba7a34ae2db2f7ec64736f6c63430008070033");

    private static final Address CONTRACT_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e7");

    private static final Address SENDER_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e6");

    private static final Address RECEIVER_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e5");

    private static final Address TOKEN_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e4");

    private final ContractCallService contractCallService;

    @Test
    void pureCall() {
        //multiplySimpleNumbers()
        final var pureFuncHash = "8070450f";
        final var successfulReadResponse =
                "0x0000000000000000000000000000000000000000000000000000000000000004";
        final var serviceParameters = serviceParameters(pureFuncHash, 0);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);
    }

    @Test
    void viewCall() {
        //returnStorageData()
        final var viewFuncHash =
                "0x6601c296000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000036b75720000000000000000000000000000000000000000000000000000000000";
        final var successfulReadResponse =
                "0x4746573740000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(viewFuncHash, 0);

        persistEntities(false);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);
    }


    @Test
    void transferFunds() {
        final var serviceParameters = serviceParameters("0x", 7L);
        persistEntities(true);

        assertThatCode(() -> contractCallService.processCall(serviceParameters)).doesNotThrowAnyException();
    }

    @Test
    void balanceCall() {
        //getAccountBalance(address)
        final var balanceCall =
                "0x93423e9c00000000000000000000000000000000000000000000000000000000000004e6";
        final var expectedBalance =
                "0x0000000000000000000000000000000000000000000000000000000000004e20";
        final var params = serviceParameters(balanceCall, 0);

        persistEntities(false);

        final var isSuccessful = contractCallService.processCall(params);
        assertThat(isSuccessful).isEqualTo(expectedBalance);
    }

    @Test
    void invalidFunctionSig() {
        final var wrongFunctionSignature = "0x542ec32e";
        final var serviceParameters = serviceParameters(wrongFunctionSignature, 0);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).isInstanceOf(InvalidTransactionException.class)
                .hasMessage("CONTRACT_REVERT_EXECUTED");
    }

    @Test
    void transferNegative() {
        final var serviceParameters = serviceParameters("0x", -5L);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void transferExceedsBalance() {
        final var serviceParameters = serviceParameters("0x", 210000L);
        persistEntities(true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).isInstanceOf(InvalidTransactionException.class);
    }

    /**
     * _to.transfer(msg.value) fails due to the static frame,{@link CallOperation} this will be
     * supported with future release with gas_estimate support.
     */
    @Test
    void transferThruContract() {
        //transferHbarsToAddress(address)
        final var stateChangePayable =
                "0x80b9f03c00000000000000000000000000000000000000000000000000000000000004e6";
        final var params = serviceParameters(stateChangePayable, 90L);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(params)).
                isInstanceOf(InvalidTransactionException.class)
                .hasMessage(LOCAL_CALL_MODIFICATION_EXCEPTION.toString());
    }

    @Test
    void stateChangeFails() {
        //writeToStorageSlot(string)
        final var stateChangeHash =
                "0x9ac27b62000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000033233320000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(stateChangeHash, 0);

        persistEntities(false);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters)).
                isInstanceOf(InvalidTransactionException.class);
    }

    private CallServiceParameters serviceParameters(String callData, long value) {
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
