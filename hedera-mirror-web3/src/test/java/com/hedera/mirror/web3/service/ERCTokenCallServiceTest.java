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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ERCTokenCallServiceTest extends Web3IntegrationTest {
    private static final Bytes ERC_CONTRACT_BYTES = Bytes.fromHexString(
            "608060405234801561001057600080fd5b50600436106100cf5760003560e01c8063d5d03e211161008c578063e7092b4111610066578063e7092b4114610270578063e9dc63751461028c578063f49f40db146102bc578063f7888aec146102ec576100cf565b8063d5d03e21146101e0578063e1f21c6714610210578063e4dc2aa414610240576100cf565b806301984892146100d4578063098f236614610104578063367605ca14610134578063927da10514610150578063a86e357614610180578063d449a832146101b0575b600080fd5b6100ee60048036038101906100e99190610a18565b61031c565b6040516100fb9190610ad5565b60405180910390f35b61011e60048036038101906101199190610b2d565b610399565b60405161012b9190610b7c565b60405180910390f35b61014e60048036038101906101499190610bcf565b61041d565b005b61016a60048036038101906101659190610c22565b61048f565b6040516101779190610c84565b60405180910390f35b61019a60048036038101906101959190610a18565b610516565b6040516101a79190610ad5565b60405180910390f35b6101ca60048036038101906101c59190610a18565b610593565b6040516101d79190610cbb565b60405180910390f35b6101fa60048036038101906101f59190610b2d565b61060b565b6040516102079190610b7c565b60405180910390f35b61022a60048036038101906102259190610cd6565b61068f565b6040516102379190610d38565b60405180910390f35b61025a60048036038101906102559190610a18565b610718565b6040516102679190610c84565b60405180910390f35b61028a60048036038101906102859190610c22565b610790565b005b6102a660048036038101906102a19190610b2d565b610812565b6040516102b39190610ad5565b60405180910390f35b6102d660048036038101906102d19190610c22565b61089b565b6040516102e39190610d38565b60405180910390f35b61030660048036038101906103019190610d53565b610922565b6040516103139190610c84565b60405180910390f35b60608173ffffffffffffffffffffffffffffffffffffffff166306fdde036040518163ffffffff1660e01b8152600401600060405180830381865afa158015610369573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906103929190610eb9565b9050919050565b60008273ffffffffffffffffffffffffffffffffffffffff1663081812fc836040518263ffffffff1660e01b81526004016103d49190610c84565b602060405180830381865afa1580156103f1573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906104159190610f17565b905092915050565b8273ffffffffffffffffffffffffffffffffffffffff1663a22cb46583836040518363ffffffff1660e01b8152600401610458929190610f44565b600060405180830381600087803b15801561047257600080fd5b505af1158015610486573d6000803e3d6000fd5b50505050505050565b60008373ffffffffffffffffffffffffffffffffffffffff1663dd62ed3e84846040518363ffffffff1660e01b81526004016104cc929190610f6d565b602060405180830381865afa1580156104e9573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061050d9190610fab565b90509392505050565b60608173ffffffffffffffffffffffffffffffffffffffff166395d89b416040518163ffffffff1660e01b8152600401600060405180830381865afa158015610563573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f8201168201806040525081019061058c9190610eb9565b9050919050565b60008173ffffffffffffffffffffffffffffffffffffffff1663313ce5676040518163ffffffff1660e01b8152600401602060405180830381865afa1580156105e0573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906106049190611004565b9050919050565b60008273ffffffffffffffffffffffffffffffffffffffff16636352211e836040518263ffffffff1660e01b81526004016106469190610c84565b602060405180830381865afa158015610663573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906106879190610f17565b905092915050565b60008373ffffffffffffffffffffffffffffffffffffffff1663095ea7b384846040518363ffffffff1660e01b81526004016106cc929190611031565b6020604051808303816000875af11580156106eb573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061070f919061106f565b90509392505050565b60008173ffffffffffffffffffffffffffffffffffffffff166318160ddd6040518163ffffffff1660e01b8152600401602060405180830381865afa158015610765573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906107899190610fab565b9050919050565b8273ffffffffffffffffffffffffffffffffffffffff1663dd62ed3e83836040518363ffffffff1660e01b81526004016107cb929190610f6d565b602060405180830381865afa1580156107e8573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061080c9190610fab565b50505050565b60608273ffffffffffffffffffffffffffffffffffffffff1663c87b56dd836040518263ffffffff1660e01b815260040161084d9190610c84565b600060405180830381865afa15801561086a573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906108939190610eb9565b905092915050565b60008373ffffffffffffffffffffffffffffffffffffffff1663e985e9c584846040518363ffffffff1660e01b81526004016108d8929190610f6d565b602060405180830381865afa1580156108f5573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610919919061106f565b90509392505050565b60008273ffffffffffffffffffffffffffffffffffffffff166370a08231836040518263ffffffff1660e01b815260040161095d9190610b7c565b602060405180830381865afa15801561097a573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061099e9190610fab565b905092915050565b6000604051905090565b600080fd5b600080fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b60006109e5826109ba565b9050919050565b6109f5816109da565b8114610a0057600080fd5b50565b600081359050610a12816109ec565b92915050565b600060208284031215610a2e57610a2d6109b0565b5b6000610a3c84828501610a03565b91505092915050565b600081519050919050565b600082825260208201905092915050565b60005b83811015610a7f578082015181840152602081019050610a64565b60008484015250505050565b6000601f19601f8301169050919050565b6000610aa782610a45565b610ab18185610a50565b9350610ac1818560208601610a61565b610aca81610a8b565b840191505092915050565b60006020820190508181036000830152610aef8184610a9c565b905092915050565b6000819050919050565b610b0a81610af7565b8114610b1557600080fd5b50565b600081359050610b2781610b01565b92915050565b60008060408385031215610b4457610b436109b0565b5b6000610b5285828601610a03565b9250506020610b6385828601610b18565b9150509250929050565b610b76816109da565b82525050565b6000602082019050610b916000830184610b6d565b92915050565b60008115159050919050565b610bac81610b97565b8114610bb757600080fd5b50565b600081359050610bc981610ba3565b92915050565b600080600060608486031215610be857610be76109b0565b5b6000610bf686828701610a03565b9350506020610c0786828701610a03565b9250506040610c1886828701610bba565b9150509250925092565b600080600060608486031215610c3b57610c3a6109b0565b5b6000610c4986828701610a03565b9350506020610c5a86828701610a03565b9250506040610c6b86828701610a03565b9150509250925092565b610c7e81610af7565b82525050565b6000602082019050610c996000830184610c75565b92915050565b600060ff82169050919050565b610cb581610c9f565b82525050565b6000602082019050610cd06000830184610cac565b92915050565b600080600060608486031215610cef57610cee6109b0565b5b6000610cfd86828701610a03565b9350506020610d0e86828701610a03565b9250506040610d1f86828701610b18565b9150509250925092565b610d3281610b97565b82525050565b6000602082019050610d4d6000830184610d29565b92915050565b60008060408385031215610d6a57610d696109b0565b5b6000610d7885828601610a03565b9250506020610d8985828601610a03565b9150509250929050565b600080fd5b600080fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b610dd582610a8b565b810181811067ffffffffffffffff82111715610df457610df3610d9d565b5b80604052505050565b6000610e076109a6565b9050610e138282610dcc565b919050565b600067ffffffffffffffff821115610e3357610e32610d9d565b5b610e3c82610a8b565b9050602081019050919050565b6000610e5c610e5784610e18565b610dfd565b905082815260208101848484011115610e7857610e77610d98565b5b610e83848285610a61565b509392505050565b600082601f830112610ea057610e9f610d93565b5b8151610eb0848260208601610e49565b91505092915050565b600060208284031215610ecf57610ece6109b0565b5b600082015167ffffffffffffffff811115610eed57610eec6109b5565b5b610ef984828501610e8b565b91505092915050565b600081519050610f11816109ec565b92915050565b600060208284031215610f2d57610f2c6109b0565b5b6000610f3b84828501610f02565b91505092915050565b6000604082019050610f596000830185610b6d565b610f666020830184610d29565b9392505050565b6000604082019050610f826000830185610b6d565b610f8f6020830184610b6d565b9392505050565b600081519050610fa581610b01565b92915050565b600060208284031215610fc157610fc06109b0565b5b6000610fcf84828501610f96565b91505092915050565b610fe181610c9f565b8114610fec57600080fd5b50565b600081519050610ffe81610fd8565b92915050565b60006020828403121561101a576110196109b0565b5b600061102884828501610fef565b91505092915050565b60006040820190506110466000830185610b6d565b6110536020830184610c75565b9392505050565b60008151905061106981610ba3565b92915050565b600060208284031215611085576110846109b0565b5b60006110938482850161105a565b9150509291505056fea264697066735822122030505f246d95dbe2d809a3cec26c9d19dd45e32710561a199ad5acd555031a5364736f6c63430008110033");
    private static final Address CONTRACT_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e8");

    private static final Address SENDER_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e6");
    private static final EntityId senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());

    private static final Address RECEIVER_ADDRESS = Address.fromHexString(
            "0x00000000000000000000000000000000000004e5");
    private static final EntityId receiverEntityId = fromEvmAddress(RECEIVER_ADDRESS.toArrayUnsafe());

    private static final Address FUNGIBLE_TOKEN_ADDRESS = Address.fromHexString(
            "0x0000000000000000000000000000000000000416");
    private static final EntityId fungibleTokenEntity = fromEvmAddress(FUNGIBLE_TOKEN_ADDRESS.toArrayUnsafe());

    private static final Address NFT_TOKEN_ADDRESS = Address.fromHexString(
            "0x0000000000000000000000000000000000000417");
    private static final EntityId nftEntity = fromEvmAddress(NFT_TOKEN_ADDRESS.toArrayUnsafe());

    private final ContractCallService contractCallService;

    @BeforeEach
    void essentialDTOs() {
        final var contractBytes = ERC_CONTRACT_BYTES.toArrayUnsafe();
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

        final var senderEvmAddress = toEvmAddress(senderEntityId);

        domainBuilder.entity().customize(e ->
                        e.id(senderEntityId.getId())
                                .num(senderEntityId.getEntityNum())
                                .evmAddress(senderEvmAddress)
                                .balance(20000L)
                )
                .persist();

        final var tokenEvmAddress = toEvmAddress(fungibleTokenEntity);

        domainBuilder.entity().customize(e ->
                e.id(fungibleTokenEntity.getId())
                        .num(fungibleTokenEntity.getEntityNum())
                        .evmAddress(tokenEvmAddress)
                        .type(TOKEN).balance(1500L)).persist();

        domainBuilder.token().customize(t ->
                        t.tokenId(new TokenId(fungibleTokenEntity))
                                .treasuryAccountId(senderEntityId)
                                .totalSupply(12345L)
                                .type(FUNGIBLE_COMMON)
                                .decimals(12))
                .persist();
    }

    @Test
    void ercName() {
        final var functionHash =
                "0x019848920000000000000000000000000000000000000000000000000000000000000416";
        final var successfulResponse =
                "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000054862617273000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void ercSymbol() {
        final var functionHash =
                "0xa86e35760000000000000000000000000000000000000000000000000000000000000416";
        final var successfulResponse =
                "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000044842415200000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void ercDecimals() {
        final var functionHash =
                "0xd449a8320000000000000000000000000000000000000000000000000000000000000416";
        final var successfulResponse =
                "0x000000000000000000000000000000000000000000000000000000000000000c";
        final var serviceParameters = serviceParameters(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void totalSupply() {
        final var functionHash =
                "0xe4dc2aa40000000000000000000000000000000000000000000000000000000000000416";
        final var successfulResponse =
                "0x0000000000000000000000000000000000000000000000000000000000003039";
        final var serviceParameters = serviceParameters(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void balanceOf() {
        final var functionHash =
                "0xf7888aec000000000000000000000000000000000000000000000000000000000000041600000000000000000000000000000000000000000000000000000000000004e6";
        final var successfulResponse =
                "0x000000000000000000000000000000000000000000000000000000000000000c";
        final var serviceParameters = serviceParameters(functionHash);

        domainBuilder.tokenBalance().customize(b ->
                        b.balance(12)
                                .id(new TokenBalance.Id(1, senderEntityId, fungibleTokenEntity)))
                .persist();

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void allowance() {
        persitAllowance(true);
        final var functionHash =
                "0x927da105000000000000000000000000000000000000000000000000000000000000041600000000000000000000000000000000000000000000000000000000000004e600000000000000000000000000000000000000000000000000000000000004e5";
        final var successfulResponse =
                "0x000000000000000000000000000000000000000000000000000000000000000d";
        final var serviceParameters = serviceParameters(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    //ERC-721
    @Test
    void ownerOf() {
        persistNft();
        final var functionHash =
                "0xd5d03e2100000000000000000000000000000000000000000000000000000000000004170000000000000000000000000000000000000000000000000000000000000001";
        final var successfulResponse = "0x00000000000000000000000000000000000000000000000000000000000004e6";
        final var serviceParameters = serviceParameters(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void metadataOf() {
        persistNft();
        final var functionHash =
                "0xe9dc637500000000000000000000000000000000000000000000000000000000000004170000000000000000000000000000000000000000000000000000000000000001";
        final var emptyMetadata =
                "0x000000000000000000000000000000000000000000000000000000000000000";
        final var serviceParameters = serviceParameters(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isNotEqualTo(emptyMetadata);
    }

    @Test
    void getApproved() {
        persistNft();
        final var functionHash =
                "0x098f236600000000000000000000000000000000000000000000000000000000000004170000000000000000000000000000000000000000000000000000000000000001";
        final var successfulResponse = "0x00000000000000000000000000000000000000000000000000000000000004e5";
        final var serviceParameters = serviceParameters(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void isApprovedForAll() {
        persitAllowance(false);
        final var functionHash =
                "0xf49f40db000000000000000000000000000000000000000000000000000000000000041700000000000000000000000000000000000000000000000000000000000004e600000000000000000000000000000000000000000000000000000000000004e5";
        final var successfulResponse = "0x0000000000000000000000000000000000000000000000000000000000000001";
        final var serviceParameters = serviceParameters(functionHash);

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

    private void persistNft() {
        persistReceiver();
        domainBuilder.entity().customize(e ->
                        e.id(nftEntity.getId())
                                .num(nftEntity.getEntityNum())
                                .evmAddress(toEvmAddress(nftEntity))
                                .type(TOKEN)
                                .balance(1500L))
                .persist();

        domainBuilder.token().customize(t ->
                t.tokenId(new TokenId(nftEntity))
                        .treasuryAccountId(senderEntityId)
                        .totalSupply(12345L)
                        .type(NON_FUNGIBLE_UNIQUE)).persist();

        domainBuilder.nft().customize(n ->
                n.id(new NftId(1, nftEntity))
                        .spender(receiverEntityId)
                        .createdTimestamp(1L)
                        .modifiedTimestamp(1L)
                        .accountId(senderEntityId)).persist();
    }

    private void persitAllowance(boolean forFungible) {
        if (forFungible) {
            persistReceiver();
            domainBuilder.tokenAllowance().customize(a ->
                    a.tokenId(fungibleTokenEntity.getId())
                            .payerAccountId(senderEntityId)
                            .owner(senderEntityId.getEntityNum())
                            .spender(receiverEntityId.getEntityNum())
                            .amount(13)).persist();
        } else {
            persistNft();
            domainBuilder.nftAllowance().customize(a ->
                            a.tokenId(nftEntity.getId())
                                    .spender(receiverEntityId.getEntityNum())
                                    .owner(senderEntityId.getEntityNum())
                                    .approvedForAll(true)
                                    .payerAccountId(senderEntityId))
                    .persist();
        }
    }

    private void persistReceiver() {

        final var receiverEvmAddress = toEvmAddress(receiverEntityId);

        domainBuilder.entity().customize(e ->
                        e.id(receiverEntityId.getId())
                                .num(receiverEntityId.getEntityNum())
                                .evmAddress(receiverEvmAddress))
                .persist();
    }
}
