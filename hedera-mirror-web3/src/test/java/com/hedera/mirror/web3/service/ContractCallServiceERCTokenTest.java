/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;

class ContractCallServiceERCTokenTest extends ContractCallTestSetup {

    // Account addresses
    private static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 1043));
    private static final Address SENDER_ADDRESS_HISTORICAL = toAddress(EntityId.of(0, 0, 1014));
    private static final ByteString SENDER_PUBLIC_KEY =
            ByteString.copyFrom(Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
    private static final Address SENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));
    private static final ByteString SENDER_PUBLIC_KEY_HISTORICAL =
            ByteString.copyFrom(Hex.decode("3a2102930a39a381a68d90afc8e8c82935bd93f89800e88ec29a18e8cc13d51947c6c8"));
    private static final Address SENDER_ALIAS_HISTORICAL = Address.wrap(Bytes.wrap(
            recoverAddressFromPubKey(SENDER_PUBLIC_KEY_HISTORICAL.substring(2).toByteArray())));
    private static final Address SPENDER_ADDRESS = toAddress(EntityId.of(0, 0, 1041));
    private static final Address SPENDER_ADDRESS_HISTORICAL = toAddress(EntityId.of(0, 0, 1016));
    private static final ByteString SPENDER_PUBLIC_KEY =
            ByteString.fromHex("3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310");
    private static final Address SPENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SPENDER_PUBLIC_KEY.substring(2).toByteArray())));
    private static final ByteString SPENDER_PUBLIC_KEY_HISTORICAL =
            ByteString.fromHex("3a210398e17bcbd2926c4d8a31e32616b4754ac0a2fc71d7fb768e657db46202625f34");
    private static final Address SPENDER_ALIAS_HISTORICAL = Address.wrap(Bytes.wrap(
            recoverAddressFromPubKey(SPENDER_PUBLIC_KEY_HISTORICAL.substring(2).toByteArray())));
    private static final Address OWNER_ADDRESS = toAddress(EntityId.of(0, 0, 1044));
    private static final Address OWNER_ADDRESS_HISTORICAL = toAddress(EntityId.of(0, 0, 1065));
    private static final Address HOLLOW_ACCOUNT_ALIAS = Address.wrap(Bytes.wrap(recoverAddressFromPubKey(
            ByteString.fromHex("3a2103a159d37177894bb0491e493d1f4db8ed359ebee15a76ebd8406759a9050410a7")
                    .substring(2)
                    .toByteArray())));

    // Contract addresses
    private static final Address REDIRECT_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1265));

    // Token addresses
    private static final Address FUNGIBLE_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1046));
    private static final Address FUNGIBLE_TOKEN_ADDRESS_HISTORICAL = toAddress(EntityId.of(0, 0, 1062));
    private static final Address NFT_ADDRESS = toAddress(EntityId.of(0, 0, 1047));
    private static final Address NFT_ADDRESS_HISTORICAL = toAddress(EntityId.of(0, 0, 1063));
    private static final Address NFT_TRANSFER_ADDRESS = toAddress(EntityId.of(0, 0, 1051));
    private static final Address TREASURY_TOKEN_ADDRESS = toAddress(EntityId.of(0, 0, 1049));

    private static final byte[] KEY_PROTO = new byte[] {
            58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
            77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };
    private static final long EVM_V_34_BLOCK = 50L;

    private static final ToLongFunction<String> longValueOf =
            value -> Bytes.fromHexString(value).toLong();

    @Value("classpath:contracts/ERCTestContract/ERCTestContract.json")
    private Path ERC_ABI_PATH;
    @Value("classpath:contracts/RedirectTestContract/RedirectTestContract.json")
    private Path REDIRECT_CONTRACT_ABI_PATH;

    private static Stream<Arguments> ercContractFunctionArgumentsProvider() {
        return Arrays.stream(ErcContractReadOnlyFunctions.values())
                .flatMap(ercFunction -> Stream.of(Arguments.of(ercFunction, true), Arguments.of(ercFunction, false)));
    }

    private static Stream<Arguments> ercContractFunctionArgumentsProviderHistoricalReadOnly() {
        List<BlockType> blockNumbers =
                List.of(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)), BlockType.of(String.valueOf(EVM_V_34_BLOCK)));

        return Arrays.stream(ErcContractReadOnlyFunctionsHistorical.values())
                .flatMap(ercFunction -> Stream.concat(
                        blockNumbers.stream().map(blockNumber -> Arguments.of(ercFunction, true, blockNumber)),
                        blockNumbers.stream().map(blockNumber -> Arguments.of(ercFunction, false, blockNumber))));
    }

    private static final String REDIRECT_SUFFIX = "Redirect";
    private static final String NON_STATIC_SUFFIX = "NonStatic";

    @BeforeEach
    void setUp() {
        exchangeRatesPersist();
        feeSchedulesPersist();
    }

    @ParameterizedTest
    @MethodSource("ercContractFunctionArgumentsProvider")
    void ercReadOnlyPrecompileOperationsTest(final ErcContractReadOnlyFunctions ercFunction, final boolean isStatic) {
        persistForErcSupportedErcReadOnlyPrecompileOperationsTest();
        final Address ercContractAddress = toAddress(EntityId.of(0, 0, 1258));
        
        final var functionName = ercFunction.getName(isStatic);
        final var functionHash =
                functionEncodeDecoder.functionHashFor(functionName, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, ercContractAddress, ETH_CALL, 0L, BlockType.LATEST);

        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                ercFunction.name, ERC_ABI_PATH, ercFunction.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    private void persistForErcSupportedErcReadOnlyPrecompileOperationsTest() {
        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();

        final var ownerEntityId = ownerEntityPersist();
        final var senderEntityId = senderEntityPersist();
        final var spenderEntityId = spenderEntityPersist();
        ercContractPersist();

        final var tokenEntityId = fungibleTokenPersist(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId = nftPersist(
                NFT_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true);
        tokenAccountPersist(senderEntityId, tokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(senderEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        allowancesPersist(senderEntityId, spenderEntityId, tokenEntityId, nftEntityId);
    }

    @ParameterizedTest
    @MethodSource("ercContractFunctionArgumentsProviderHistoricalReadOnly")
    void ercReadOnlyPrecompileHistoricalOperationsTest(
            final ErcContractReadOnlyFunctionsHistorical ercFunction,
            final boolean isStatic,
            final BlockType blockNumber) {
        persistForErcReadOnlyPrecompileHistoricalOperationsTest();
        final Address ercContractAddress = toAddress(EntityId.of(0, 0, 1258));
        
        final var functionName = ercFunction.getName(isStatic);
        final var functionHash =
                functionEncodeDecoder.functionHashFor(functionName, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, ercContractAddress, ETH_CALL, 0L, blockNumber);

        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                ercFunction.name, ERC_ABI_PATH, ercFunction.expectedResultFields);

        // Before the block the data did not exist yet
        if (blockNumber.number() < EVM_V_34_BLOCK) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class);
        } else {
            assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
        }
    }

    private void persistForErcReadOnlyPrecompileHistoricalOperationsTest() {
        recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();

        historicalDataPersist();
        ercContractPersist();
    }

    @ParameterizedTest
    @ValueSource(longs = {51, Long.MAX_VALUE - 1})
    void ercReadOnlyPrecompileHistoricalNotExistingBlockTest(final long blockNumber) {
        final Address ercContractAddress = toAddress(EntityId.of(0, 0, 1258));
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "isApprovedForAll",
                ERC_ABI_PATH,
                NFT_ADDRESS_HISTORICAL,
                SENDER_ADDRESS_HISTORICAL,
                SPENDER_ADDRESS_HISTORICAL);
        recordFileEvm46Latest = domainBuilder.recordFile().persist();

        final var serviceParameters = serviceParametersForExecution(
                functionHash, ercContractAddress, ETH_CALL, 0L, BlockType.of(String.valueOf(blockNumber)));
        final var latestBlockNumber = recordFileRepository.findLatestIndex().orElse(Long.MAX_VALUE);

        // Block number (Long.MAX_VALUE - 1) does not exist in the DB and is after the
        // latest block available in the DB => returning error
        if (blockNumber > latestBlockNumber) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(BlockNumberOutOfRangeException.class);
        } else if (blockNumber == 51) {
            // Block number 51 = (EVM_V_34_BLOCK + 1) does not exist in the DB but it is before the latest
            // block available in the DB => throw an exception
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(BlockNumberNotFoundException.class);
        }
    }

    @ParameterizedTest
    @MethodSource("ercContractFunctionArgumentsProvider")
    void supportedErcReadOnlyPrecompileOperationsTest(
            final ErcContractReadOnlyFunctions ercFunction, final boolean isStatic) {
        // persist needed entity
        recordFileEvm46Latest = domainBuilder.recordFile().persist();
        
        final Address ercContractAddress = toAddress(EntityId.of(0, 0, 1258));
        final var functionName = ercFunction.getName(isStatic);
        final var functionHash =
                functionEncodeDecoder.functionHashFor(functionName, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, ercContractAddress, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(ErcContractModificationFunctions.class)
    void supportedErcModificationPrecompileOperationsTest(final ErcContractModificationFunctions ercFunction) {
        // persist needed entity
        recordFileEvm46Latest = domainBuilder.recordFile().persist();

        final Address ercContractAddress = toAddress(EntityId.of(0, 0, 1258));
        final var functionHash =
                functionEncodeDecoder.functionHashFor(ercFunction.name, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, ercContractAddress, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(ErcContractReadOnlyFunctions.class)
    void supportedErcReadOnlyRedirectPrecompileOperationsTest(final ErcContractReadOnlyFunctions ercFunction) {
        // persist needed entity
        recordFileEvm46Latest = domainBuilder.recordFile().persist();

        final var functionName = ercFunction.name + REDIRECT_SUFFIX;
        final var functionHash = functionEncodeDecoder.functionHashFor(
                functionName, REDIRECT_CONTRACT_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, REDIRECT_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(ErcContractReadOnlyFunctionsNegative.class)
    void supportedErcReadOnlyRedirectPrecompileNegativeOperationsTest(
            final ErcContractReadOnlyFunctionsNegative ercFunction) {
        persistForSupportedErcReadOnlyRedirectPrecompileNegativeOperationsTest();

        final var functionName = ercFunction.name + REDIRECT_SUFFIX;
        final var functionHash = functionEncodeDecoder.functionHashFor(
                functionName, REDIRECT_CONTRACT_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, REDIRECT_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
    }

    void persistForSupportedErcReadOnlyRedirectPrecompileNegativeOperationsTest() {
        recordFileEvm46Latest = domainBuilder.recordFile().persist();
        redirectContractPersist();
        final var ownerEntityId = ownerEntityPersist();
        fungibleTokenPersist(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true);
    }

    @ParameterizedTest
    @EnumSource(ErcContractModificationFunctions.class)
    void supportedErcModificationsRedirectPrecompileOperationsTest(final ErcContractModificationFunctions ercFunction) {
        // persist needed entity
        recordFileEvm46Latest = domainBuilder.recordFile().persist();

        final var functionName = ercFunction.name + "Redirect";
        final var functionHash = functionEncodeDecoder.functionHashFor(
                functionName, REDIRECT_CONTRACT_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, REDIRECT_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @Test
    void delegateTransferDoesNotExecuteAndReturnEmpty() {
        // persist needed entity
        recordFileEvm46Latest = domainBuilder.recordFile().persist();

        final Address ercContractAddress = toAddress(EntityId.of(0, 0, 1258));
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "delegateTransfer", ERC_ABI_PATH, FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS, 2L);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, ercContractAddress, ETH_CALL, 0L, BlockType.LATEST);
        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
    }

    protected CallServiceParameters serviceParametersForExecution(
            final Bytes callData,
            final Address contractAddress,
            final CallServiceParameters.CallType callType,
            final long value,
            final BlockType block) {
        return serviceParametersForExecution(callData, contractAddress, callType, value, block, 15_000_000L);
    }

    protected CallServiceParameters serviceParametersForExecution(
            final Bytes callData,
            final Address contractAddress,
            final CallServiceParameters.CallType callType,
            final long value,
            final BlockType block,
            final long gasLimit) {
        HederaEvmAccount sender;
        if (block != BlockType.LATEST) {
            sender = new HederaEvmAccount(SENDER_ADDRESS_HISTORICAL);
        } else {
            sender = new HederaEvmAccount(SENDER_ADDRESS);
        }

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(contractAddress)
                .callData(callData)
                .gas(gasLimit)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(block)
                .build();
    }

    @Override
    protected EntityId ownerEntityPersistHistorical() {
        final var ownerEntityId = fromEvmAddress(OWNER_ADDRESS_HISTORICAL.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.
                        id(ownerEntityId.getId())
                        .num(ownerEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(ownerEntityId))
                        .balance(20000L)
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();

        return ownerEntityId;
    }

    @Override
    protected void historicalDataPersist() {
        final var ownerEntityId = ownerEntityPersistHistorical();
        final var senderEntityId = senderEntityPersistHistorical();
        final var spenderEntityId = spenderEntityPersistHistorical();
        final var autoRenewEntityIdHistorical = autoRenewAccountPersistHistorical();

        balancePersistHistorical(
                FUNGIBLE_TOKEN_ADDRESS_HISTORICAL,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        fungibleTokenPersistHistorical(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS_HISTORICAL,
                toAddress(autoRenewEntityIdHistorical),
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        nftPersistHistorical(
                NFT_ADDRESS_HISTORICAL,
                toAddress(autoRenewEntityIdHistorical),
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));

        final var tokenEntityId = fromEvmAddress(FUNGIBLE_TOKEN_ADDRESS_HISTORICAL.toArrayUnsafe());
        final var nftEntityId = fromEvmAddress(NFT_ADDRESS_HISTORICAL.toArrayUnsafe());
        // Token relationships
        tokenAccountPersistHistorical(senderEntityId, tokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersistHistorical(senderEntityId, nftEntityId, TokenFreezeStatusEnum.FROZEN);

        // Token allowances
        tokenAllowancePersistHistorical(tokenEntityId, senderEntityId, senderEntityId, spenderEntityId, 13L);
        nftAllowancePersistHistorical(nftEntityId, senderEntityId, senderEntityId, spenderEntityId);
    }

    @RequiredArgsConstructor
    private enum ErcContractReadOnlyFunctions {
        GET_APPROVED_EMPTY_SPENDER("getApproved", new Object[] {NFT_ADDRESS, 2L}, new Address[] {Address.ZERO}),
        IS_APPROVE_FOR_ALL(
                "isApprovedForAll", new Address[] {NFT_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS}, new Boolean[] {true}),
        IS_APPROVE_FOR_ALL_WITH_ALIAS(
                "isApprovedForAll", new Address[] {NFT_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS}, new Boolean[] {true}),
        ALLOWANCE_OF(
                "allowance", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS}, new Long[] {13L}),
        ALLOWANCE_OF_WITH_ALIAS(
                "allowance", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS}, new Long[] {13L}),
        GET_APPROVED("getApproved", new Object[] {NFT_ADDRESS, 1L}, new Address[] {SPENDER_ALIAS}),
        ERC_DECIMALS("decimals", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Integer[] {12}),
        TOTAL_SUPPLY("totalSupply", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Long[] {12345L}),
        ERC_SYMBOL("symbol", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new String[] {"HBAR"}),
        BALANCE_OF("balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}, new Long[] {12L}),
        BALANCE_OF_WITH_ALIAS("balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, new Long[] {12L}),
        ERC_NAME("name", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new String[] {"Hbars"}),
        OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS, 1L}, new Address[] {OWNER_ADDRESS}),
        EMPTY_OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS, 2L}, new Address[] {Address.ZERO}),
        TOKEN_URI("tokenURI", new Object[] {NFT_ADDRESS, 1L}, new String[] {"NFT_METADATA_URI"});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;

        public String getName(final boolean isStatic) {
            return isStatic ? name : name + NON_STATIC_SUFFIX;
        }
    }

    @RequiredArgsConstructor
    public enum ErcContractReadOnlyFunctionsNegative {
        // Negative scenarios - expected to throw an exception
        ERC_DECIMALS_NEGATIVE("decimals", new Address[] {NFT_ADDRESS}),
        OWNER_OF_NEGATIVE("getOwnerOf", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 1L}),
        TOKEN_URI_NEGATIVE("tokenURI", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 1L});

        private final String name;
        private final Object[] functionParameters;
    }

    @RequiredArgsConstructor
    public enum ErcContractReadOnlyFunctionsHistorical {
        GET_APPROVED_EMPTY_SPENDER(
                "getApproved", new Object[] {NFT_ADDRESS_HISTORICAL, 2L}, new Address[] {Address.ZERO}),
        IS_APPROVE_FOR_ALL(
                "isApprovedForAll",
                new Address[] {NFT_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL, SPENDER_ADDRESS_HISTORICAL},
                new Boolean[] {true}),
        IS_APPROVE_FOR_ALL_WITH_ALIAS(
                "isApprovedForAll",
                new Address[] {NFT_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL, SPENDER_ALIAS_HISTORICAL},
                new Boolean[] {true}),
        ALLOWANCE_OF(
                "allowance",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL, SPENDER_ADDRESS_HISTORICAL
                },
                new Long[] {13L}),
        ALLOWANCE_OF_WITH_ALIAS(
                "allowance",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL, SPENDER_ALIAS_HISTORICAL},
                new Long[] {13L}),
        GET_APPROVED(
                "getApproved", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new Address[] {SPENDER_ALIAS_HISTORICAL}),
        ERC_DECIMALS("decimals", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Integer[] {12}),
        TOTAL_SUPPLY("totalSupply", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Long[] {12345L}),
        ERC_SYMBOL("symbol", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new String[] {"HBAR"}),
        BALANCE_OF(
                "balanceOf",
                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL},
                new Long[] {12L}),
        BALANCE_OF_WITH_ALIAS(
                "balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL}, new Long[] {12L
                }),
        ERC_NAME("name", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new String[] {"Hbars"}),
        OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new Address[] {OWNER_ADDRESS_HISTORICAL}),
        EMPTY_OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS_HISTORICAL, 2L}, new Address[] {Address.ZERO}),
        TOKEN_URI("tokenURI", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new String[] {"NFT_METADATA_URI"});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;

        public String getName(final boolean isStatic) {
            return isStatic ? name : name + NON_STATIC_SUFFIX;
        }
    }

    @RequiredArgsConstructor
    public enum ErcContractModificationFunctions {
        APPROVE("approve", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS, 2L}),
        DELETE_ALLOWANCE_NFT("approve", new Object[] {NFT_ADDRESS, Address.ZERO, 1L}),
        APPROVE_NFT("approve", new Object[] {NFT_ADDRESS, SPENDER_ADDRESS, 1L}),
        APPROVE_WITH_ALIAS("approve", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS, 2L}),
        TRANSFER("transfer", new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, 2L}),
        TRANSFER_FROM("transferFrom", new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS, 2L}),
        TRANSFER_FROM_TO_HOLLOW_ACCOUNT(
                "transferFrom", new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, HOLLOW_ACCOUNT_ALIAS, 1L}),
        TRANSFER_FROM_NFT("transferFromNFT", new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L}),
        TRANSFER_WITH_ALIAS("transfer", new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, 2L}),
        TRANSFER_FROM_WITH_ALIAS(
                "transferFrom", new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS, 2L}),
        TRANSFER_FROM_NFT_WITH_ALIAS(
                "transferFromNFT", new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L});

        private final String name;
        private final Object[] functionParameters;
    }
}
