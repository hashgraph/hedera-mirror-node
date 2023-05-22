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
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.mirror.web3.utils.FunctionEncodeDecoder.convertAddress;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FRACTIONAL_FEE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.CustomFee.FeeCase;
import java.nio.file.Path;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractCallServicePrecompileTest extends Web3IntegrationTest {
    // The contract source `PrecompileTestContract.sol` is in test/resources
    @Value("classpath:contracts/PrecompileTestContract/PrecompileTestContract.bin")
    private Path CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/PrecompileTestContract/PrecompileTestContract.json")
    private Path ABI_PATH;

    @Value("classpath:contracts/ModificationPrecompileTestContract/ModificationPrecompileTestContract.bin")
    private Path MODIFICATION_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/ModificationPrecompileTestContract/ModificationPrecompileTestContract.json")
    private Path MODIFICATION_CONTRACT_ABI_PATH;

    private static final String ERROR_MESSAGE = "Precompile not supported for non-static frames";
    private static final Address MODIFICATION_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1256, CONTRACT));
    private static final Address CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1255, CONTRACT));
    private static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 1254, ACCOUNT));
    private static final Address FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1252, TOKEN));
    private static final Address NFT_ADDRESS = toAddress(EntityId.of(0, 0, 1251, TOKEN));
    private static final byte[] KEY_PROTO = new byte[] {
        58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
        77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };
    private static final byte[] ECDSA_KEY = Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length);
    private static final Address ETH_ADDRESS = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
    private static final Address EMPTY_ADDRESS = Address.wrap(Bytes.wrap(new byte[20]));
    private final ContractCallService contractCallService;
    private final FunctionEncodeDecoder encodeDecoder;

    @ParameterizedTest
    @EnumSource(ContractReadFunctions.class)
    void evmPrecompileReadOnlyTokenFunctionsTest(ContractReadFunctions contractFunc) {
        final var functionHash =
                encodeDecoder.functionHashFor(contractFunc.name, ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForEthCall(functionHash, true);
        final var successfulResponse =
                encodeDecoder.encodedResultFor(contractFunc.name, ABI_PATH, contractFunc.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(ContractReadFunctions.class)
    void evmPrecompileReadOnlyTokenFunctionsTestWithNonStaticFrame(ContractReadFunctions contractFunc) {
        final var functionHash =
                encodeDecoder.functionHashFor(contractFunc.name, ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForEthCall(functionHash, false);
        final var successfulResponse =
                encodeDecoder.encodedResultFor(contractFunc.name, ABI_PATH, contractFunc.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(UnsupportedContractModificationFunctions.class)
    void evmPrecompileUnsupportedModificationTokenFunctionsTest(UnsupportedContractModificationFunctions contractFunc) {
        final var functionHash = encodeDecoder.functionHashWithEmptyDataFor(
                contractFunc.name, MODIFICATION_CONTRACT_ABI_PATH, contractFunc.functionParameters);
        final var serviceParameters = serviceParametersForEthEstimateGas(functionHash);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage(ERROR_MESSAGE);
    }

    @ParameterizedTest
    @EnumSource(FeeCase.class)
    void customFees(FeeCase feeCase) {
        final var functionName = "getCustomFeesForToken";
        final var functionHash = encodeDecoder.functionHashFor(functionName, ABI_PATH, FUNGIBLE_TOKEN_ADDRESS);
        final var serviceParameters = serviceParametersForEthCall(functionHash, true);
        customFeesPersist(feeCase);

        final var callResult = contractCallService.processCall(serviceParameters);
        final var decodeResult = encodeDecoder.decodeResult(functionName, ABI_PATH, callResult);
        final Tuple[] fixedFee = decodeResult.get(0);
        final Tuple[] fractionalFee = decodeResult.get(1);
        final Tuple[] royaltyFee = decodeResult.get(2);

        switch (feeCase) {
            case FIXED_FEE -> {
                assertThat((long) fixedFee[0].get(0)).isEqualTo(100L);
                assertThat((com.esaulpaugh.headlong.abi.Address) fixedFee[0].get(1))
                        .isEqualTo(convertAddress(FUNGIBLE_TOKEN_ADDRESS));
                assertThat((boolean) fixedFee[0].get(2)).isFalse();
                assertThat((boolean) fixedFee[0].get(3)).isFalse();
                assertThat((com.esaulpaugh.headlong.abi.Address) fixedFee[0].get(4))
                        .isEqualTo(convertAddress(SENDER_ADDRESS));
            }
            case FRACTIONAL_FEE -> {
                assertThat((long) fractionalFee[0].get(0)).isEqualTo(100L);
                assertThat((long) fractionalFee[0].get(1)).isEqualTo(10L);
                assertThat((long) fractionalFee[0].get(2)).isEqualTo(1L);
                assertThat((long) fractionalFee[0].get(3)).isEqualTo(1000L);
                assertThat((boolean) fractionalFee[0].get(4)).isTrue();
                assertThat((com.esaulpaugh.headlong.abi.Address) fractionalFee[0].get(5))
                        .isEqualTo(convertAddress(SENDER_ADDRESS));
            }
            case ROYALTY_FEE -> {
                assertThat((long) royaltyFee[0].get(0)).isEqualTo(20L);
                assertThat((long) royaltyFee[0].get(1)).isEqualTo(10L);
                assertThat((long) royaltyFee[0].get(2)).isEqualTo(100L);
                assertThat((com.esaulpaugh.headlong.abi.Address) royaltyFee[0].get(3))
                        .isEqualTo(convertAddress(FUNGIBLE_TOKEN_ADDRESS));
                assertThat((boolean) royaltyFee[0].get(4)).isFalse();
                assertThat((com.esaulpaugh.headlong.abi.Address) royaltyFee[0].get(5))
                        .isEqualTo(convertAddress(SENDER_ADDRESS));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"getInformationForFungibleToken,false", "getInformationForNonFungibleToken,true"})
    void getTokenInfo(String functionName, boolean isNft) {
        final var functionHash = isNft
                ? encodeDecoder.functionHashFor(functionName, ABI_PATH, NFT_ADDRESS, 1L)
                : encodeDecoder.functionHashFor(functionName, ABI_PATH, FUNGIBLE_TOKEN_ADDRESS);
        final var serviceParameters = serviceParametersForEthCall(functionHash, true);
        customFeesPersist(FRACTIONAL_FEE);

        final var callResult = contractCallService.processCall(serviceParameters);
        final Tuple decodeResult =
                encodeDecoder.decodeResult(functionName, ABI_PATH, callResult).get(0);
        Tuple tokenInfo = decodeResult.get(0);
        Tuple hederaToken = tokenInfo.get(0);
        boolean deleted = tokenInfo.get(2);
        boolean defaultKycStatus = tokenInfo.get(3);
        boolean pauseStatus = tokenInfo.get(4);
        Tuple[] fractionalFees = tokenInfo.get(6);
        String ledgerId = tokenInfo.get(8);
        String name = hederaToken.get(0);
        String symbol = hederaToken.get(1);
        com.esaulpaugh.headlong.abi.Address treasury = hederaToken.get(2);
        String memo = hederaToken.get(3);
        boolean supplyType = hederaToken.get(4);
        long maxSupply = hederaToken.get(5);
        boolean freezeStatus = hederaToken.get(6);
        Tuple expiry = hederaToken.get(8);
        com.esaulpaugh.headlong.abi.Address autoRenewAccount = expiry.get(1);
        long autoRenewPeriod = expiry.get(2);

        assertThat(deleted).isFalse();
        assertThat(defaultKycStatus).isFalse();
        assertThat(pauseStatus).isTrue();
        assertThat(fractionalFees).isNotEmpty();
        assertThat(ledgerId).isEqualTo("0x01");
        assertThat(name).isEqualTo("Hbars");
        assertThat(symbol).isEqualTo("HBAR");
        assertThat(treasury).isEqualTo(convertAddress(SENDER_ADDRESS));
        assertThat(memo).isEqualTo("TestMemo");
        assertThat(freezeStatus).isTrue();
        assertThat(autoRenewPeriod).isEqualTo(1800L);

        if (isNft) {
            long serialNum = decodeResult.get(1);
            com.esaulpaugh.headlong.abi.Address owner = decodeResult.get(2);
            long creationTime = decodeResult.get(3);
            byte[] metadata = decodeResult.get(4);
            com.esaulpaugh.headlong.abi.Address spender = decodeResult.get(5);

            assertThat(serialNum).isEqualTo(1L);
            assertThat(owner).isEqualTo(convertAddress(SENDER_ADDRESS));
            assertThat(creationTime).isEqualTo(1475067194949034022L);
            assertThat(metadata).isNotEmpty();
            assertThat(spender).isEqualTo(convertAddress(SENDER_ADDRESS));
            assertThat(maxSupply).isEqualTo(1L);
            assertThat(supplyType).isTrue();
            assertThat(autoRenewAccount).isEqualTo(convertAddress(NFT_ADDRESS));
        } else {
            int decimals = decodeResult.get(1);
            long totalSupply = tokenInfo.get(1);
            assertThat(decimals).isEqualTo(1000);
            assertThat(totalSupply).isEqualTo(1_000_000_000L);
            assertThat(maxSupply).isEqualTo(2525L);
            assertThat(supplyType).isFalse();
            assertThat(autoRenewAccount).isEqualTo(convertAddress(FUNGIBLE_TOKEN_ADDRESS));
        }
    }

    @Test
    void nftInfoForInvalidSerialNo() {
        final var functionHash =
                encodeDecoder.functionHashFor("getInformationForNonFungibleToken", ABI_PATH, NFT_ADDRESS, 4L);
        final var serviceParameters = serviceParametersForEthCall(functionHash, true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void tokenInfoForNonTokenAccount() {
        final var functionHash =
                encodeDecoder.functionHashFor("getInformationForFungibleToken", ABI_PATH, SENDER_ADDRESS);
        final var serviceParameters = serviceParametersForEthCall(functionHash, true);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void notExistingPrecompileCallFails() {
        final var functionHash = encodeDecoder.functionHashFor(
                "callNotExistingPrecompile", MODIFICATION_CONTRACT_ABI_PATH, FUNGIBLE_TOKEN_ADDRESS);
        final var serviceParameters = serviceParametersForEthEstimateGas(functionHash);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage(ERROR_MESSAGE);
    }

    @RequiredArgsConstructor
    enum ContractReadFunctions {
        IS_FROZEN("isTokenFrozen", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}, new Boolean[] {true}),
        IS_FROZEN_ETH_ADDRESS(
                "isTokenFrozen", new Address[] {FUNGIBLE_TOKEN_ADDRESS, ETH_ADDRESS}, new Boolean[] {true}),
        IS_KYC("isKycGranted", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}, new Boolean[] {true}),
        IS_KYC_FOR_NFT("isKycGranted", new Address[] {NFT_ADDRESS, SENDER_ADDRESS}, new Boolean[] {false}),
        IS_TOKEN_PRECOMPILE("isTokenAddress", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Boolean[] {true}),
        IS_TOKEN_PRECOMPILE_NFT("isTokenAddress", new Address[] {NFT_ADDRESS}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_KYC("getTokenDefaultKyc", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_KYC_NFT("getTokenDefaultKyc", new Address[] {NFT_ADDRESS}, new Boolean[] {true}),
        GET_TOKEN_TYPE("getType", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Long[] {0L}),
        GET_TOKEN_TYPE_FOR_NFT("getType", new Address[] {NFT_ADDRESS}, new Long[] {1L}),
        GET_TOKEN_DEFAULT_FREEZE("getTokenDefaultFreeze", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Boolean[] {true}),
        GET_TOKEN_DEFAULT_FREEZE_FOR_NFT("getTokenDefaultFreeze", new Address[] {NFT_ADDRESS}, new Boolean[] {true}),
        GET_TOKEN_ADMIN_KEY("getTokenKeyPublic", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 1L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_FREEZE_KEY("getTokenKeyPublic", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 4L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_WIPE_KEY("getTokenKeyPublic", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 8L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_SUPPLY_KEY("getTokenKeyPublic", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 16L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_KYC_KEY_FOR_NFT("getTokenKeyPublic", new Object[] {NFT_ADDRESS, 2L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_FEE_KEY_FOR_NFT("getTokenKeyPublic", new Object[] {NFT_ADDRESS, 32L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        }),
        GET_TOKEN_PAUSE_KEY_FOR_NFT("getTokenKeyPublic", new Object[] {NFT_ADDRESS, 64L}, new Object[] {
            false, Address.ZERO, new byte[0], ECDSA_KEY, Address.ZERO
        });

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }

    @RequiredArgsConstructor
    enum UnsupportedContractModificationFunctions {
        CRYPTO_TRANSFER("cryptoTransferExternal", new Object[] {
            new Object[] {EMPTY_ADDRESS, 0L, false},
            new Object[] {EMPTY_ADDRESS, new Object[] {EMPTY_ADDRESS, 0L, false}},
            new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS, 0L, false}
        }),
        MINT_TOKEN("mintTokenExternal", new Object[] {EMPTY_ADDRESS, 0L, new byte[0]}),
        BURN_TOKEN("burnTokenExternal", new Object[] {EMPTY_ADDRESS, 0L, new long[0]}),
        ASSOCIATE_TOKEN("associateTokenExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS}),
        ASSOCIATE_TOKENS("associateTokensExternal", new Object[] {EMPTY_ADDRESS, new Address[0]}),
        DISSOCIATE_TOKEN("dissociateTokenExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS}),
        DISSOCIATE_TOKENS("dissociateTokensExternal", new Object[] {EMPTY_ADDRESS, new Address[0]}),
        CREATE_FUNGIBLE_TOKEN("createFungibleTokenExternal", new Object[] {new Object[] {}, 0L, 0}),
        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES(
                "createFungibleTokenWithCustomFeesExternal",
                new Object[] {new Object[] {}, 0L, 0, new Object[] {}, new Object[] {}}),
        CREATE_NON_FUNGIBLE_TOKEN("createNonFungibleTokenExternal", new Object[] {new Object[] {}}),
        CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES(
                "createNonFungibleTokenWithCustomFeesExternal",
                new Object[] {new Object[] {}, new Object[] {}, new Object[] {}}),
        APPROVE("approveExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS, 0L}),
        TRANSFER_FROM("transferFromExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS, EMPTY_ADDRESS, 0L}),
        TRANSFER_FROM_NFT("transferFromNFTExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS, EMPTY_ADDRESS, 0L}),
        APPROVE_NFT("approveNFTExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS, 0L}),
        FREEZE_TOKEN("freezeTokenExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS}),
        GRANT_TOKEN_KYC("grantTokenKycExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS}),
        REVOKE_TOKEN_KYC("revokeTokenKycExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS}),
        SET_APPROVAL_FOR_ALL("setApprovalForAllExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS, false}),
        TRANSFER_TOKENS("transferTokensExternal", new Object[] {EMPTY_ADDRESS, new Address[0], new long[0]}),
        TRANSFER_NFT_TOKENS(
                "transferNFTsExternal", new Object[] {EMPTY_ADDRESS, new Address[0], new Address[0], new long[0]}),
        TRANSFER_TOKEN("transferTokenExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS, EMPTY_ADDRESS, 0L}),
        TRANSFER_NFT_TOKEN("transferNFTExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS, EMPTY_ADDRESS, 0L}),
        PAUSE_TOKEN("pauseTokenExternal", new Object[] {EMPTY_ADDRESS}),
        UNPAUSE_TOKEN("unpauseTokenExternal", new Object[] {EMPTY_ADDRESS}),
        WIPE_TOKEN("wipeTokenAccountExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS, 0L}),
        WIPE_NFT_TOKEN("wipeTokenAccountNFTExternal", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS, new long[0]}),
        DELETE_TOKEN("deleteTokenExternal", new Object[] {EMPTY_ADDRESS}),
        UPDATE_TOKEN_KEYS("updateTokenKeysExternal", new Object[] {EMPTY_ADDRESS, new Object[] {}}),
        UPDATE_TOKEN_EXPIRY("updateTokenExpiryInfoExternal", new Object[] {EMPTY_ADDRESS, new Object[] {}}),
        UPDATE_TOKEN_INFO("updateTokenInfoExternal", new Object[] {EMPTY_ADDRESS, new Object[] {}}),
        REDIRECT_FOR_TOKEN("getBalanceOfWithDirectRedirect", new Object[] {EMPTY_ADDRESS, EMPTY_ADDRESS});

        private final String name;
        private final Object[] functionParameters;
    }

    private CallServiceParameters serviceParametersForEthCall(Bytes callData, boolean isStatic) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        persistEntities();

        return CallServiceParameters.builder()
                .sender(sender)
                .value(0L)
                .receiver(CONTRACT_ADDRESS)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(isStatic)
                .callType(ETH_CALL)
                .build();
    }

    private CallServiceParameters serviceParametersForEthEstimateGas(Bytes callData) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        persistEntities();

        return CallServiceParameters.builder()
                .sender(sender)
                .value(0L)
                .receiver(MODIFICATION_CONTRACT_ADDRESS)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(false)
                .isEstimate(true)
                .callType(ETH_ESTIMATE_GAS)
                .build();
    }

    private void persistEntities() {
        final var contractBytes = encodeDecoder.getContractBytes(CONTRACT_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(CONTRACT_ADDRESS.toArrayUnsafe());
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

        final var modificationContractBytes = encodeDecoder.getContractBytes(MODIFICATION_CONTRACT_BYTES_PATH);
        final var modificationContractEntityId = fromEvmAddress(MODIFICATION_CONTRACT_ADDRESS.toArrayUnsafe());
        final var modificationContractEvmAddress = toEvmAddress(modificationContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(modificationContractEntityId.getId())
                        .num(modificationContractEntityId.getEntityNum())
                        .evmAddress(modificationContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(modificationContractEntityId.getId()).runtimeBytecode(modificationContractBytes))
                .persist();

        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getEntityNum())
                        .evmAddress(null)
                        .balance(20000L))
                .persist();

        final var ethAccount = 358L;

        domainBuilder
                .entity()
                .customize(e -> e.id(ethAccount)
                        .num(ethAccount)
                        .evmAddress(ETH_ADDRESS.toArrayUnsafe())
                        .balance(2000L))
                .persist();

        final var tokenEntityId = fromEvmAddress(FUNGIBLE_TOKEN_ADDRESS.toArrayUnsafe());
        final var tokenEvmAddress = toEvmAddress(tokenEntityId);
        final var key = KEY_PROTO;

        domainBuilder
                .entity()
                .customize(e -> e.id(tokenEntityId.getId())
                        .autoRenewAccountId(tokenEntityId.getId())
                        .num(tokenEntityId.getEntityNum())
                        .evmAddress(tokenEvmAddress)
                        .type(TOKEN)
                        .balance(1500L)
                        .key(key)
                        .memo("TestMemo"))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(new TokenId(tokenEntityId))
                        .treasuryAccountId(EntityId.of(0, 0, senderEntityId.getId(), ACCOUNT))
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .kycKey(key)
                        .freezeDefault(true)
                        .feeScheduleKey(key)
                        .supplyType(TokenSupplyTypeEnum.INFINITE)
                        .maxSupply(2525L)
                        .wipeKey(key)
                        .freezeKey(key)
                        .pauseStatus(TokenPauseStatusEnum.PAUSED)
                        .pauseKey(key)
                        .supplyKey(key))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .accountId(senderEntityId.getId())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .accountId(ethAccount)
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();

        final var nftEntityId = fromEvmAddress(NFT_ADDRESS.toArrayUnsafe());
        final var nftEvmAddress = toEvmAddress(nftEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(nftEntityId.getId())
                        .autoRenewAccountId(nftEntityId.getId())
                        .expirationTimestamp(null)
                        .num(nftEntityId.getEntityNum())
                        .evmAddress(nftEvmAddress)
                        .type(TOKEN)
                        .balance(1500L)
                        .key(key)
                        .memo("TestMemo"))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(new TokenId(nftEntityId))
                        .treasuryAccountId(EntityId.of(0, 0, senderEntityId.getId(), ACCOUNT))
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycKey(key)
                        .freezeDefault(true)
                        .feeScheduleKey(key)
                        .maxSupply(1L)
                        .supplyType(TokenSupplyTypeEnum.FINITE)
                        .freezeKey(key)
                        .pauseKey(key)
                        .pauseStatus(TokenPauseStatusEnum.PAUSED)
                        .wipeKey(key)
                        .supplyKey(key)
                        .wipeKey(key))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.id(new NftId(1L, nftEntityId))
                        .accountId(EntityId.of(0, 0, senderEntityId.getId(), ACCOUNT))
                        .createdTimestamp(1475067194949034022L)
                        .spender(senderEntityId)
                        .metadata(new byte[] {1, 2, 3})
                        .modifiedTimestamp(1475067194949034022L))
                .persist();

        domainBuilder
                .customFee()
                .customize(f -> f.collectorAccountId(senderEntityId)
                        .id(new CustomFee.Id(2L, nftEntityId))
                        .royaltyDenominator(0L)
                        .denominatingTokenId(nftEntityId))
                .persist();
    }

    private void customFeesPersist(FeeCase feeCase) {
        var collectorAccountId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());
        var tokenEntityId = fromEvmAddress(FUNGIBLE_TOKEN_ADDRESS.toArrayUnsafe());
        var timeStamp = System.currentTimeMillis();
        switch (feeCase) {
            case ROYALTY_FEE -> domainBuilder
                    .customFee()
                    .customize(f -> f.collectorAccountId(collectorAccountId)
                            .id(new CustomFee.Id(timeStamp, tokenEntityId))
                            .denominatingTokenId(tokenEntityId))
                    .persist();
            case FRACTIONAL_FEE -> domainBuilder
                    .customFee()
                    .customize(f -> f.collectorAccountId(collectorAccountId)
                            .id(new CustomFee.Id(timeStamp, tokenEntityId))
                            .royaltyDenominator(0L)
                            .denominatingTokenId(tokenEntityId))
                    .persist();
            case FIXED_FEE -> domainBuilder
                    .customFee()
                    .customize(f -> f.collectorAccountId(collectorAccountId)
                            .id(new CustomFee.Id(timeStamp, tokenEntityId))
                            .royaltyDenominator(0L)
                            .amountDenominator(null)
                            .royaltyNumerator(0L)
                            .denominatingTokenId(tokenEntityId))
                    .persist();
            default -> domainBuilder
                    .customFee()
                    .customize(f -> f.collectorAccountId(null)
                            .id(new CustomFee.Id(timeStamp, tokenEntityId))
                            .royaltyDenominator(0L)
                            .royaltyNumerator(0L)
                            .denominatingTokenId(tokenEntityId))
                    .persist();
        }
    }
}
