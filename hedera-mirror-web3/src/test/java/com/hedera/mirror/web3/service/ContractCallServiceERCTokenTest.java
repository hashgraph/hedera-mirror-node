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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractCallServiceERCTokenTest extends Web3IntegrationTest {
    private static final Address CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1256, CONTRACT));
    private static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 742, ACCOUNT));
    private static final Address RECEIVER_ADDRESS = toAddress(EntityId.of(0, 0, 741, ACCOUNT));
    private static final Address FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1046, TOKEN));
    private static final Address NFT_ADDRESS = toAddress(EntityId.of(0, 0, 1047, TOKEN));
    private final ContractCallService contractCallService;
    private final FunctionEncodeDecoder functionEncodeDecoder;
    private final MirrorNodeEvmProperties properties;
    // The contract source `ERCTestContract.sol` is in test resources
    @Value("classpath:contracts/ERCTestContract/ERCTestContract.bin")
    private Path CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/ERCTestContract/ERCTestContract.json")
    private Path ABI_PATH;

    @ParameterizedTest
    @EnumSource(ContractReadOnlyFunctions.class)
    void ercReadOnlyPrecompileOperationsTest(ContractReadOnlyFunctions ercFunction) {
        properties.setAllowanceEnabled(true);
        properties.setApprovedForAllEnabled(true);

        final var functionHash =
                functionEncodeDecoder.functionHashFor(ercFunction.name, ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForEthCall(functionHash);
        final var successfulResponse =
                functionEncodeDecoder.encodedResultFor(ercFunction.name, ABI_PATH, ercFunction.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(ContractModificationFunctions.class)
    void ercModificationPrecompileOperationsTest(ContractModificationFunctions ercFunction) {
        properties.setAllowanceEnabled(true);
        properties.setApprovedForAllEnabled(true);

        final var functionHash =
                functionEncodeDecoder.functionHashFor(ercFunction.name, ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForEthEstimateGas(functionHash);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Precompile not supported for non-static frames");
    }

    @Test
    void metadataOf() {
        final var functionHash = functionEncodeDecoder.functionHashFor("tokenURI", ABI_PATH, NFT_ADDRESS, 1L);
        final var serviceParameters = serviceParametersForEthCall(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isNotEqualTo(Address.ZERO.toString());
    }

    @Test
    void delegateTransferDoesNotExecuteAndReturnEmpty() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "delegateTransfer", ABI_PATH, FUNGIBLE_TOKEN_ADDRESS, RECEIVER_ADDRESS, 2L);
        final var serviceParameters = serviceParametersForEthCall(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(Bytes.EMPTY.toHexString());
    }

    @Test
    void unsupportedApprovePrecompileTest() {
        properties.setAllowanceEnabled(false);

        final var functionHash = functionEncodeDecoder.functionHashFor(
                "allowance", ABI_PATH, FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, RECEIVER_ADDRESS);
        final var serviceParameters = serviceParametersForEthCall(functionHash);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("allowance(address owner, address spender) is not supported.");
    }

    @Test
    void unsupportedIsApprovedForAllPrecompileTest() {
        properties.setApprovedForAllEnabled(false);

        final var functionHash = functionEncodeDecoder.functionHashFor(
                "isApprovedForAll", ABI_PATH, NFT_ADDRESS, SENDER_ADDRESS, RECEIVER_ADDRESS);
        final var serviceParameters = serviceParametersForEthCall(functionHash);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("isApprovedForAll(address owner, address operator) is not supported.");
    }

    private CallServiceParameters serviceParametersForEthCall(final Bytes callData) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        persistEntities();

        return CallServiceParameters.builder()
                .sender(sender)
                .value(0L)
                .receiver(CONTRACT_ADDRESS)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(true)
                .callType(ETH_CALL)
                .build();
    }

    private CallServiceParameters serviceParametersForEthEstimateGas(final Bytes callData) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        persistEntities();

        return CallServiceParameters.builder()
                .sender(sender)
                .value(0L)
                .receiver(CONTRACT_ADDRESS)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(ETH_ESTIMATE_GAS)
                .build();
    }

    private void persistEntities() {
        final var contractBytes = functionEncodeDecoder.getContractBytes(CONTRACT_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(CONTRACT_ADDRESS.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);
        final var receiverEntityId = fromEvmAddress(RECEIVER_ADDRESS.toArrayUnsafe());
        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());
        final var fungibleTokenEntity = fromEvmAddress(FUNGIBLE_TOKEN_ADDRESS.toArrayUnsafe());
        final var nftEntity = fromEvmAddress(NFT_ADDRESS.toArrayUnsafe());

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
                .customize(c -> c.id(contractEntityId.getId()).runtimeBytecode(contractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(contractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder.recordFile().customize(f -> f.bytes(contractBytes)).persist();

        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getEntityNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(senderEntityId))
                        .balance(20000L))
                .persist();

        final var tokenEvmAddress = toEvmAddress(fungibleTokenEntity);

        domainBuilder
                .entity()
                .customize(e -> e.id(fungibleTokenEntity.getId())
                        .num(fungibleTokenEntity.getEntityNum())
                        .evmAddress(tokenEvmAddress)
                        .type(TOKEN)
                        .balance(1500L))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(new TokenId(fungibleTokenEntity))
                        .treasuryAccountId(senderEntityId)
                        .totalSupply(12345L)
                        .type(FUNGIBLE_COMMON)
                        .decimals(12))
                .persist();

        final var receiverEvmAddress = toEvmAddress(receiverEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(receiverEntityId.getId())
                        .num(receiverEntityId.getEntityNum())
                        .evmAddress(receiverEvmAddress))
                .persist();

        domainBuilder
                .entity()
                .customize(e -> e.id(nftEntity.getId())
                        .num(nftEntity.getEntityNum())
                        .evmAddress(toEvmAddress(nftEntity))
                        .type(TOKEN)
                        .balance(1500L))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(new TokenId(nftEntity))
                        .treasuryAccountId(senderEntityId)
                        .totalSupply(12345L)
                        .type(NON_FUNGIBLE_UNIQUE))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.id(new NftId(1, nftEntity))
                        .spender(receiverEntityId)
                        .createdTimestamp(1L)
                        .modifiedTimestamp(1L)
                        .accountId(senderEntityId))
                .persist();

        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(fungibleTokenEntity.getId())
                        .payerAccountId(senderEntityId)
                        .owner(senderEntityId.getEntityNum())
                        .spender(receiverEntityId.getEntityNum())
                        .amount(13))
                .persist();

        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(nftEntity.getId())
                        .spender(receiverEntityId.getEntityNum())
                        .owner(senderEntityId.getEntityNum())
                        .approvedForAll(true)
                        .payerAccountId(senderEntityId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.balance(12).accountId(senderEntityId.getId()).tokenId(fungibleTokenEntity.getId()))
                .persist();
    }

    @RequiredArgsConstructor
    public enum ContractReadOnlyFunctions {
        GET_APPROVED_EMPTY_SPENDER("getApproved", new Object[] {NFT_ADDRESS, 2L}, new Address[] {Address.ZERO}),
        IS_APPROVE_FOR_ALL(
                "isApprovedForAll", new Address[] {NFT_ADDRESS, SENDER_ADDRESS, RECEIVER_ADDRESS}, new Boolean[] {true
                }),
        ALLOWANCE_OF(
                "allowance", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, RECEIVER_ADDRESS}, new Long[] {13L
                }),
        GET_APPROVED("getApproved", new Object[] {NFT_ADDRESS, 1L}, new Address[] {RECEIVER_ADDRESS}),
        ERC_DECIMALS("decimals", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Integer[] {12}),
        TOTAL_SUPPLY("totalSupply", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Long[] {12345L}),
        ERC_SYMBOL("symbol", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new String[] {"HBAR"}),
        BALANCE_OF("balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}, new Long[] {12L}),
        ERC_NAME("name", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new String[] {"Hbars"}),
        OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS, 1L}, new Address[] {SENDER_ADDRESS}),
        EMPTY_OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS, 2L}, new Address[] {Address.ZERO});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }

    @RequiredArgsConstructor
    public enum ContractModificationFunctions {
        TRANSFER("transfer", new Object[] {FUNGIBLE_TOKEN_ADDRESS, RECEIVER_ADDRESS, 2L}),
        TRANSFER_FROM("transferFrom", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, RECEIVER_ADDRESS, 2L}),
        APPROVE("approve", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, 2L});

        private final String name;
        private final Object[] functionParameters;
    }
}
