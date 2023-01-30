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
import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ERCTokenCallServiceTest extends Web3IntegrationTest {
    private static final Bytes ERC_CONTRACT_BYTES = Bytes.fromHexString(
            "608060405234801561001057600080fd5b50600436106100a95760003560e01c8063d449a83211610071578063d449a83214610176578063e1f21c67146101a6578063e4dc2aa4146101d6578063e7092b4114610206578063f49f40db14610222578063f7888aec1461023e576100a9565b806301984892146100ae578063098f2366146100de578063367605ca146100fa578063927da10514610116578063a86e357614610146575b600080fd5b6100c860048036038101906100c39190610853565b61026e565b6040516100d59190610910565b60405180910390f35b6100f860048036038101906100f39190610968565b6102eb565b005b610114600480360381019061010f91906109e0565b61036a565b005b610130600480360381019061012b9190610a33565b6103dc565b60405161013d9190610a95565b60405180910390f35b610160600480360381019061015b9190610853565b610463565b60405161016d9190610910565b60405180910390f35b610190600480360381019061018b9190610853565b6104e0565b60405161019d9190610acc565b60405180910390f35b6101c060048036038101906101bb9190610ae7565b610558565b6040516101cd9190610b49565b60405180910390f35b6101f060048036038101906101eb9190610853565b6105e1565b6040516101fd9190610a95565b60405180910390f35b610220600480360381019061021b9190610a33565b610659565b005b61023c60048036038101906102379190610a33565b6106db565b005b61025860048036038101906102539190610b64565b61075d565b6040516102659190610a95565b60405180910390f35b60608173ffffffffffffffffffffffffffffffffffffffff166306fdde036040518163ffffffff1660e01b8152600401600060405180830381865afa1580156102bb573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906102e49190610cca565b9050919050565b8173ffffffffffffffffffffffffffffffffffffffff1663081812fc826040518263ffffffff1660e01b81526004016103249190610a95565b602060405180830381865afa158015610341573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906103659190610d28565b505050565b8273ffffffffffffffffffffffffffffffffffffffff1663a22cb46583836040518363ffffffff1660e01b81526004016103a5929190610d64565b600060405180830381600087803b1580156103bf57600080fd5b505af11580156103d3573d6000803e3d6000fd5b50505050505050565b60008373ffffffffffffffffffffffffffffffffffffffff1663dd62ed3e84846040518363ffffffff1660e01b8152600401610419929190610d8d565b602060405180830381865afa158015610436573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061045a9190610dcb565b90509392505050565b60608173ffffffffffffffffffffffffffffffffffffffff166395d89b416040518163ffffffff1660e01b8152600401600060405180830381865afa1580156104b0573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906104d99190610cca565b9050919050565b60008173ffffffffffffffffffffffffffffffffffffffff1663313ce5676040518163ffffffff1660e01b8152600401602060405180830381865afa15801561052d573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906105519190610e24565b9050919050565b60008373ffffffffffffffffffffffffffffffffffffffff1663095ea7b384846040518363ffffffff1660e01b8152600401610595929190610e51565b6020604051808303816000875af11580156105b4573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906105d89190610e8f565b90509392505050565b60008173ffffffffffffffffffffffffffffffffffffffff166318160ddd6040518163ffffffff1660e01b8152600401602060405180830381865afa15801561062e573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906106529190610dcb565b9050919050565b8273ffffffffffffffffffffffffffffffffffffffff1663dd62ed3e83836040518363ffffffff1660e01b8152600401610694929190610d8d565b602060405180830381865afa1580156106b1573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906106d59190610dcb565b50505050565b8273ffffffffffffffffffffffffffffffffffffffff1663e985e9c583836040518363ffffffff1660e01b8152600401610716929190610d8d565b602060405180830381865afa158015610733573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906107579190610e8f565b50505050565b60008273ffffffffffffffffffffffffffffffffffffffff166370a08231836040518263ffffffff1660e01b81526004016107989190610ebc565b602060405180830381865afa1580156107b5573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906107d99190610dcb565b905092915050565b6000604051905090565b600080fd5b600080fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b6000610820826107f5565b9050919050565b61083081610815565b811461083b57600080fd5b50565b60008135905061084d81610827565b92915050565b600060208284031215610869576108686107eb565b5b60006108778482850161083e565b91505092915050565b600081519050919050565b600082825260208201905092915050565b60005b838110156108ba57808201518184015260208101905061089f565b60008484015250505050565b6000601f19601f8301169050919050565b60006108e282610880565b6108ec818561088b565b93506108fc81856020860161089c565b610905816108c6565b840191505092915050565b6000602082019050818103600083015261092a81846108d7565b905092915050565b6000819050919050565b61094581610932565b811461095057600080fd5b50565b6000813590506109628161093c565b92915050565b6000806040838503121561097f5761097e6107eb565b5b600061098d8582860161083e565b925050602061099e85828601610953565b9150509250929050565b60008115159050919050565b6109bd816109a8565b81146109c857600080fd5b50565b6000813590506109da816109b4565b92915050565b6000806000606084860312156109f9576109f86107eb565b5b6000610a078682870161083e565b9350506020610a188682870161083e565b9250506040610a29868287016109cb565b9150509250925092565b600080600060608486031215610a4c57610a4b6107eb565b5b6000610a5a8682870161083e565b9350506020610a6b8682870161083e565b9250506040610a7c8682870161083e565b9150509250925092565b610a8f81610932565b82525050565b6000602082019050610aaa6000830184610a86565b92915050565b600060ff82169050919050565b610ac681610ab0565b82525050565b6000602082019050610ae16000830184610abd565b92915050565b600080600060608486031215610b0057610aff6107eb565b5b6000610b0e8682870161083e565b9350506020610b1f8682870161083e565b9250506040610b3086828701610953565b9150509250925092565b610b43816109a8565b82525050565b6000602082019050610b5e6000830184610b3a565b92915050565b60008060408385031215610b7b57610b7a6107eb565b5b6000610b898582860161083e565b9250506020610b9a8582860161083e565b9150509250929050565b600080fd5b600080fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b610be6826108c6565b810181811067ffffffffffffffff82111715610c0557610c04610bae565b5b80604052505050565b6000610c186107e1565b9050610c248282610bdd565b919050565b600067ffffffffffffffff821115610c4457610c43610bae565b5b610c4d826108c6565b9050602081019050919050565b6000610c6d610c6884610c29565b610c0e565b905082815260208101848484011115610c8957610c88610ba9565b5b610c9484828561089c565b509392505050565b600082601f830112610cb157610cb0610ba4565b5b8151610cc1848260208601610c5a565b91505092915050565b600060208284031215610ce057610cdf6107eb565b5b600082015167ffffffffffffffff811115610cfe57610cfd6107f0565b5b610d0a84828501610c9c565b91505092915050565b600081519050610d2281610827565b92915050565b600060208284031215610d3e57610d3d6107eb565b5b6000610d4c84828501610d13565b91505092915050565b610d5e81610815565b82525050565b6000604082019050610d796000830185610d55565b610d866020830184610b3a565b9392505050565b6000604082019050610da26000830185610d55565b610daf6020830184610d55565b9392505050565b600081519050610dc58161093c565b92915050565b600060208284031215610de157610de06107eb565b5b6000610def84828501610db6565b91505092915050565b610e0181610ab0565b8114610e0c57600080fd5b50565b600081519050610e1e81610df8565b92915050565b600060208284031215610e3a57610e396107eb565b5b6000610e4884828501610e0f565b91505092915050565b6000604082019050610e666000830185610d55565b610e736020830184610a86565b9392505050565b600081519050610e89816109b4565b92915050565b600060208284031215610ea557610ea46107eb565b5b6000610eb384828501610e7a565b91505092915050565b6000602082019050610ed16000830184610d55565b9291505056fea2646970667358221220f4a16d4ed2be2d13c066c477ab1e6f6b635405bb2847ee2a8552fe9b3f0a84fd64736f6c63430008110033");
    private static final Address CONTRACT_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e8");

    private static final Address SENDER_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e6");

    private static final Address RECEIVER_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e5");

    private static final Address TOKEN_ADDRESS = Address.fromHexString(
            "0x0000000000000000000000000000000000000416");

    private final ContractCallService contractCallService;
    private boolean isFungable = true;

    @Test
    void ercName() {
        final var functionHash =
                "0x019848920000000000000000000000000000000000000000000000000000000000000416";
        final var successfulResponse =
                "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000054862617273000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(functionHash);

        persistEntities();

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void ercSymbol() {
        final var functionHash =
                "0xa86e35760000000000000000000000000000000000000000000000000000000000000416";
        final var successfulResponse =
                "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000044842415200000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(functionHash);

        persistEntities();

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void ercDecimals() {
        final var functionHash =
                "0xd449a8320000000000000000000000000000000000000000000000000000000000000416";
        final var successfulResponse =
                "0x000000000000000000000000000000000000000000000000000000000000000c";
        final var serviceParameters = serviceParameters(functionHash);

        persistEntities();

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void totalSupply() {
        final var functionHash =
                "0xe4dc2aa40000000000000000000000000000000000000000000000000000000000000416";
        final var successfulResponse =
                "0x0000000000000000000000000000000000000000000000000000000000003039";
        final var serviceParameters = serviceParameters(functionHash);

        persistEntities();

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void balanceOf() {
        final var functionHash =
                "0xf7888aec000000000000000000000000000000000000000000000000000000000000041600000000000000000000000000000000000000000000000000000000000004e6";
        final var successfulResponse =
                "0x000000000000000000000000000000000000000000000000000000000000000c";
        final var serviceParameters = serviceParameters(functionHash);

        persistEntities();

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void allowance() {
        final var functionHash =
                "0x927da105000000000000000000000000000000000000000000000000000000000000041600000000000000000000000000000000000000000000000000000000000004e600000000000000000000000000000000000000000000000000000000000004e5";
        final var successfulResponse =
                "0x0000000000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(functionHash);

        persistEntities();

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    private CallServiceParameters serviceParameters(String callData) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        final var data = Bytes.fromHexString(callData);

        return CallServiceParameters.builder()
                .sender(sender)
                .value(0)
                .receiver(CONTRACT_ADDRESS)
                .callData(data)
                .providedGasLimit(120000000L)
                .isStatic(true)
                .build();
    }

    private void persistEntities() {
        final var contractBytes = ERC_CONTRACT_BYTES.toArrayUnsafe();
        final var receiverEntityId = fromEvmAddress(RECEIVER_ADDRESS.toArrayUnsafe());
        final var receiverEvmAddress = toEvmAddress(receiverEntityId);

        domainBuilder.entity().customize(e ->
                        e.id(receiverEntityId.getId())
                                .num(receiverEntityId.getEntityNum())
                                .evmAddress(receiverEvmAddress))
                .persist();

        final var contractEntityId = fromEvmAddress(CONTRACT_ADDRESS.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        domainBuilder.entity().customize(e ->
                e.id(contractEntityId.getId())
                        .num(contractEntityId.getEntityNum())
                        .evmAddress(contractEvmAddress)
                        .type(CONTRACT).balance(1500L)).persist();

        domainBuilder.contract().customize(c ->
                        c.id(contractEntityId.getId())
                                .runtimeBytecode(contractBytes))
                .persist();

        domainBuilder.contractState().customize(c ->
                        c.contractId(contractEntityId.getId())
                                .slot(Bytes.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000000").toArrayUnsafe())
                                .value(Bytes.fromHexString(
                                        "0x4746573740000000000000000000000000000000000000000000000000000000").toArrayUnsafe()))
                .persist();

        domainBuilder.recordFile().customize(f ->
                f.bytes(contractBytes)).persist();

        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());
        final var senderEvmAddress = toEvmAddress(senderEntityId);

        domainBuilder.entity().customize(e ->
                        e.id(senderEntityId.getId())
                                .num(senderEntityId.getEntityNum())
                                .evmAddress(senderEvmAddress)
                                .balance(20000L)
                )
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
                                .treasuryAccountId(senderEntityId)
                                .totalSupply(12345L)
                                .type(isFungable ?
                                        FUNGIBLE_COMMON :
                                        NON_FUNGIBLE_UNIQUE
                                ).decimals(12))
                .persist();
        domainBuilder.customFee().customize(f ->
                        f.denominatingTokenId(tokenEntityId)
                                .collectorAccountId(senderEntityId)
                                .id(new CustomFee.Id(domainBuilder.timestamp(), tokenEntityId))
                )
                .persist();
        domainBuilder.tokenBalance().customize(b ->
                        b.balance(12)
                                .id(new TokenBalance.Id(1, senderEntityId, tokenEntityId)))
                .persist();
    }
}
