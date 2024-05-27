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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.ToLongFunction;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

class ContractCallEvmCodesTest extends Web3IntegrationTest {

    private static final byte[] KEY_PROTO = new byte[] {
        58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
        77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };

    private static final ByteString SENDER_PUBLIC_KEY =
            ByteString.copyFrom(Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));

    private static final Address SENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));

    private static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 1043));

    private static final Address EVM_CODES_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1263));

    private static final long EVM_V_34_BLOCK = 50L;

    private static final ToLongFunction<String> longValueOf =
            value -> Bytes.fromHexString(value).toLong();

    @Value("classpath:contracts/EvmCodes/EvmCodes.json")
    private Path EVM_CODES_ABI_PATH;

    // The contract sources `EthCall.sol` and `Reverter.sol` are in test/resources
    @Value("classpath:contracts/EthCall/EthCall.bin")
    private Path ETH_CALL_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/TestContractAddress/TestNestedAddressThis.bin")
    private Path NESTED_ADDRESS_THIS_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/NestedCallsTestContract/NestedCallsTestContract.bin")
    private Path NESTED_CALLS_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/TestContractAddress/TestAddressThis.json")
    private Path ADDRESS_THIS_CONTRACT_ABI_PATH;

    @Value("classpath:contracts/TestContractAddress/TestAddressThis.bin")
    private Path ADDRESS_THIS_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/EvmCodes/EvmCodes.bin")
    private Path EVM_CODES_BYTES_PATH;

    private static final Address ADDRESS_THIS_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1269));

    @Value("classpath:contracts/TestContractAddress/TestAddressThisInit.bin")
    private Path ADDRESS_THIS_CONTRACT_INIT_BYTES_PATH;

    @Autowired
    private FunctionEncodeDecoder functionEncodeDecoder;

    @Autowired
    private MirrorEvmTxProcessor processor;

    @Autowired
    private ContractCallService contractCallService;

    @Autowired
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    private boolean areEntitiesPersisted;

    private static RecordFile recordFileForBlockHash;
    private static RecordFile genesisRecordFileForBlockHash;
    private static RecordFile recordFileBeforeEvm34;
    private static RecordFile recordFileAfterEvm34;

    @Test
    void chainId() {
        final var functionHash = functionEncodeDecoder.functionHashFor("chainId", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(mirrorNodeEvmProperties.chainIdBytes32().toHexString());
    }

    protected void persistEntities() {
        historicalBlocksPersist();
        evmCodesContractPersist();
        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .evmAddress(SENDER_ALIAS.toArray())
                        .deleted(false)
                        .alias(SENDER_PUBLIC_KEY.toByteArray())
                        .balance(10000 * 100_000_000L))
                .persist();
        exchangeRatesPersist();
        feeSchedulesPersist();
    }

    @Nullable
    private EntityId autoRenewAccountPersistHistorical() {
        final var autoRenewEntityId =
                fromEvmAddress(toAddress(EntityId.of(0, 0, 1078)).toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(autoRenewEntityId.getId())
                        .num(autoRenewEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(autoRenewEntityId))
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();

        return autoRenewEntityId;
    }

    private EntityId addressThisContractPersist() {
        final var addressThisContractBytes = functionEncodeDecoder.getContractBytes(ADDRESS_THIS_CONTRACT_BYTES_PATH);
        final var addressThisContractEntityId = fromEvmAddress(ADDRESS_THIS_CONTRACT_ADDRESS.toArrayUnsafe());
        final var addressThisEvmAddress = toEvmAddress(addressThisContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(addressThisContractEntityId.getId())
                        .num(addressThisContractEntityId.getNum())
                        .evmAddress(addressThisEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(addressThisContractEntityId.getId()).runtimeBytecode(addressThisContractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(addressThisContractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(addressThisContractBytes))
                .persist();
        return addressThisContractEntityId;
    }

    private void ethCallContractPersist() {
        Address ethCallContractAddress = toAddress(EntityId.of(0, 0, 1260));
        final var ethCallContractBytes = functionEncodeDecoder.getContractBytes(ETH_CALL_CONTRACT_BYTES_PATH);
        final var ethCallContractEntityId = fromEvmAddress(ethCallContractAddress.toArrayUnsafe());
        final var ethCallContractEvmAddress = toEvmAddress(ethCallContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(ethCallContractEntityId.getId())
                        .num(ethCallContractEntityId.getNum())
                        .evmAddress(ethCallContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(ethCallContractEntityId.getId()).runtimeBytecode(ethCallContractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(ethCallContractEntityId.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder.recordFile().customize(f -> f.bytes(ethCallContractBytes)).persist();
    }

    // Contracts persist
    private void evmCodesContractPersist() {
        final var evmCodesContractBytes = functionEncodeDecoder.getContractBytes(EVM_CODES_BYTES_PATH);
        final var evmCodesContractEntityId = fromEvmAddress(EVM_CODES_CONTRACT_ADDRESS.toArrayUnsafe());
        final var evmCodesContractEvmAddress = toEvmAddress(evmCodesContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(evmCodesContractEntityId.getId())
                        .num(evmCodesContractEntityId.getNum())
                        .evmAddress(evmCodesContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(evmCodesContractEntityId.getId()).runtimeBytecode(evmCodesContractBytes))
                .persist();

        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(evmCodesContractBytes))
                .persist();
    }

    private void feeSchedulesPersist() {
        CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(EthereumTransaction)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build()))))
                .build();
        final var feeScheduleEntityId = EntityId.of(0L, 0L, 111L);
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray())
                        .entityId(feeScheduleEntityId)
                        .consensusTimestamp(EXPIRY + 1))
                .persist();
    }

    private void exchangeRatesPersist() {
        final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
                .setNextRate(ExchangeRate.newBuilder()
                        .setCentEquiv(15)
                        .setHbarEquiv(1)
                        .build())
                .build();
        EntityId exchangeRateEntityId = EntityId.of(0L, 0L, 112L);
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(exchangeRateEntityId)
                        .consensusTimestamp(EXPIRY))
                .persist();
    }

    @Test
    void ECRECPrecompiledContract() {
        final var functionHash = functionEncodeDecoder.functionHashFor("recoverAddress", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters)).isNotEmpty();
    }

    @Test
    void SHA256PrecompiledContract() {
        final var functionHash = functionEncodeDecoder.functionHashFor("calculateSHA256", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        final var result = contractCallService.processCall(serviceParameters);
        final var decodedResult = functionEncodeDecoder.decodeResult("calculateSHA256", EVM_CODES_ABI_PATH, result);

        assertThat(Bytes.wrap((byte[]) decodedResult.get(0)).toHexString())
                .isEqualTo("0xe93bb3ba29b71e2623d3d6e4c0f266c41cb005259e8cad8c4d04f966053ac712");
    }

    @Test
    void RIPEMD160PrecompiledContract() {
        final var functionHash = functionEncodeDecoder.functionHashFor("calculateRIPEMD160", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        final var result = contractCallService.processCall(serviceParameters);
        final var decodedResult = functionEncodeDecoder.decodeResult("calculateRIPEMD160", EVM_CODES_ABI_PATH, result);

        assertThat(Bytes.wrap((byte[]) decodedResult.get(0)).toHexString())
                .isEqualTo("0x000000000000000000000000c4861db52a25298b7ba404b6f8a65d2d6473c1a9");
    }

    @Test
    void IDPrecompiledContract() {
        final var functionHash = functionEncodeDecoder.functionHashFor("identity", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        final var result = contractCallService.processCall(serviceParameters);
        final var decodedResult = functionEncodeDecoder.decodeResult("identity", EVM_CODES_ABI_PATH, result);

        assertThat(Bytes.wrap((byte[]) decodedResult.get(0)).toHexString()).contains("48656c6c6f2c20576f726c64");
    }

    @Test
    void BigIntegerModularExponentiationPrecompiledContract() {
        final var functionHash = functionEncodeDecoder.functionHashFor("modExp", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000004");
    }

    @Test
    void AltBN128AddPrecompiledContract() {
        final var functionHash = functionEncodeDecoder.functionHashFor("addPoints", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(
                        "0x030644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd315ed738c0e0a7c92e7845f96b2ae9c0a68a6a449e3538fc7ff3ebf7a5a18a2c4");
    }

    @Test
    void AltBN128MulPrecompiledContract() {
        final var functionHash = functionEncodeDecoder.functionHashFor("multiplyPoints", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(
                        "0x0769bf9ac56bea3ff40232bcb1b6bd159315d84715b8e679f2d355961915abf02ab799bee0489429554fdb7c8d086475319e63b40b9c5b57cdf1ff3dd9fe2261");
    }

    @Test
    void AltBN128PairingPrecompiledContract() {
        final var functionHash = functionEncodeDecoder.functionHashFor("pairingCheck", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        final var TRUE = "0x0000000000000000000000000000000000000000000000000000000000000001";
        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(TRUE);
    }

    @Test
    void BLAKE2BFPrecompileContract() {
        final var functionHash = functionEncodeDecoder.functionHashFor("blake2", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(
                        "0xba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923");
    }

    @Test
    void getBlockPrevrandao() {
        final var functionHash = functionEncodeDecoder.functionHashFor("getBlockPrevrandao", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        String result = contractCallService.processCall(serviceParameters);
        assertThat(result).isNotBlank();
        assertTrue(result.length() > "0x".length());
    }

    @Test
    void getBlockHashReturnsCorrectHash() {
        // Persist all entities so that we can get a specific record file hash and pass it to
        // functionEncodeDecoder#functionHashFor.
        nestedEthCallsContractPersist();
        persistEntities();
        areEntitiesPersisted = true;

        final var functionHash = functionEncodeDecoder.functionHashFor(
                "getBlockHash", EVM_CODES_ABI_PATH, recordFileForBlockHash.getIndex());
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo("0x" + recordFileForBlockHash.getHash().substring(0, 64));
    }

    @Test
    void getGenesisBlockHashReturnsCorrectBlock() {
        // Persist all entities so that we can get a specific record file hash and pass it to
        // functionEncodeDecoder#functionHashFor.
        genesisBlockPersist();
        persistEntities();
        areEntitiesPersisted = true;

        final var functionHash = functionEncodeDecoder.functionHashFor("getBlockHash", EVM_CODES_ABI_PATH, 0L);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo("0x" + genesisRecordFileForBlockHash.getHash().substring(0, 64));
    }

    @Test
    void getLatestBlockHashIsNotEmpty() {
        final var functionHash = functionEncodeDecoder.functionHashFor("getLatestBlockHash", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isNotEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000");
    }

    @Test
    void getBlockHashAfterTheLatestReturnsZero() {
        final var functionHash =
                functionEncodeDecoder.functionHashFor("getBlockHash", EVM_CODES_ABI_PATH, Long.MAX_VALUE);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000");
    }

    @ParameterizedTest
    @CsvSource({
        // function getCodeHash with parameter hedera system accounts or accounts that do not exist
        // expected to revert with INVALID_SOLIDITY_ADDRESS
        "0x81ea44080000000000000000000000000000000000000000000000000000000000000167",
        "0x81ea44080000000000000000000000000000000000000000000000000000000000000168",
        "0x81ea44080000000000000000000000000000000000000000000000000000000000000169",
        "0x81ea440800000000000000000000000000000000000000000000000000000000000005ee",
        "0x81ea440800000000000000000000000000000000000000000000000000000000000005e4",
    })
    void testSystemContractCodeHashPreVersion38(String input) {
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(input),
                EVM_CODES_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.of(String.valueOf(EVM_V_34_BLOCK)),
                SENDER_ADDRESS);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class);
    }

    @ParameterizedTest
    @CsvSource({
        // function getCodeHash with parameter hedera system accounts, expected 0 bytes
        "0x81ea44080000000000000000000000000000000000000000000000000000000000000167, 0x0000000000000000000000000000000000000000000000000000000000000000",
        "0x81ea44080000000000000000000000000000000000000000000000000000000000000168, 0x0000000000000000000000000000000000000000000000000000000000000000",
        "0x81ea440800000000000000000000000000000000000000000000000000000000000002ee, 0x0000000000000000000000000000000000000000000000000000000000000000",
        "0x81ea440800000000000000000000000000000000000000000000000000000000000002e4, 0x0000000000000000000000000000000000000000000000000000000000000000",
    })
    void testSystemContractCodeHash(String input, String expectedOutput) {
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(input), EVM_CODES_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST, SENDER_ADDRESS);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(expectedOutput);
    }

    @ParameterizedTest
    @CsvSource({
        // function getCodeHash with parameter contract, expected keccak256 of the contract bytecode
        "0x81ea440800000000000000000000000000000000000000000000000000000000000004ec, 0x9674ad57ff4dac4fdcf84dcf4053ec4b91481d2f4a3fcd483564898e73ad4228",
        // function getCodeHash with parameter account, expected  keccak256 of empty string
        "0x81ea44080000000000000000000000000000000000000000000000000000000000000436, 0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470",
    })
    void testNonSystemContractCodeHash(String input, String expectedOutput) {
        ethCallContractPersist();
        autoRenewAccountPersistHistorical();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(input), EVM_CODES_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST, SENDER_ADDRESS);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(expectedOutput);
    }

    @Test
    void deployAddressThisContract() {
        final var serviceParameters = serviceParametersForAddressThis(
                Bytes.wrap(functionEncodeDecoder.getContractBytes(ADDRESS_THIS_CONTRACT_INIT_BYTES_PATH)));
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    private static boolean isWithinExpectedGasRange(final long actualGas, final long expectedGas) {
        return actualGas >= (expectedGas * 1.05) && actualGas <= (expectedGas * 1.20);
    }

    @Test
    void addressThisFromFunction() {
        final var functionHash =
                functionEncodeDecoder.functionHashFor("testAddressThis", ADDRESS_THIS_CONTRACT_ABI_PATH);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, ADDRESS_THIS_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST, SENDER_ADDRESS);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @Test
    void addressThisEthCallWithoutEvmAlias() {
        addressThisContractPersist();
        String addressThisContractAddressWithout0x =
                ADDRESS_THIS_CONTRACT_ADDRESS.toString().substring(2);
        String successfulResponse = "0x000000000000000000000000" + addressThisContractAddressWithout0x;
        final var functionHash =
                functionEncodeDecoder.functionHashFor("getAddressThis", ADDRESS_THIS_CONTRACT_ABI_PATH);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, ADDRESS_THIS_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST, SENDER_ADDRESS);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @Test
    void deployNestedAddressThisContract() {
        final var serviceParameters = serviceParametersForAddressThis(
                Bytes.wrap(functionEncodeDecoder.getContractBytes(NESTED_ADDRESS_THIS_CONTRACT_BYTES_PATH)));
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @Test
    void selfDestructCall() {
        // destroyContract(address)
        final var destroyContractInput = "0x016a3738000000000000000000000000" + SENDER_ALIAS.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(destroyContractInput),
                EVM_CODES_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                SENDER_ADDRESS);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
    }

    @Test
    void selfDestructCallWithSystemAccount() {
        // destroyContract(address)
        var systemAccountAddress = toAddress(EntityId.of(0, 0, 700));
        final var destroyContractInput =
                "0x016a3738000000000000000000000000" + systemAccountAddress.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(destroyContractInput),
                EVM_CODES_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                SENDER_ADDRESS);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> {
                    MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                    assertEquals(exception.getMessage(), INVALID_SOLIDITY_ADDRESS.name());
                });
    }

    private CallServiceParameters serviceParametersForEvmCodes(final Bytes callData) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);

        if (!areEntitiesPersisted) {
            persistEntities();
            areEntitiesPersisted = true;
        }

        return CallServiceParameters.builder()
                .sender(sender)
                .value(0L)
                .receiver(EVM_CODES_CONTRACT_ADDRESS)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(true)
                .callType(ETH_CALL)
                .block(BlockType.LATEST)
                .build();
    }

    private CallServiceParameters serviceParametersForAddressThis(final Bytes callData) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        if (!areEntitiesPersisted) {
            persistEntities();
            areEntitiesPersisted = true;
        }

        return CallServiceParameters.builder()
                .sender(sender)
                .value(0L)
                .receiver(Address.ZERO)
                .callData(callData)
                .callType(ETH_ESTIMATE_GAS)
                .block(BlockType.LATEST)
                .gas(15_000_000L)
                .isStatic(false)
                .isEstimate(true)
                .build();
    }

    private long gasUsedAfterExecution(final CallServiceParameters serviceParameters) {
        return ContractCallContext.run(ctx -> {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            long result = processor
                    .execute(serviceParameters, serviceParameters.getGas())
                    .getGasUsed();

            assertThat(store.getStackedStateFrames().height()).isEqualTo(1);
            return result;
        });
    }

    private void genesisBlockPersist() {
        genesisRecordFileForBlockHash =
                domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
    }

    private void historicalBlocksPersist() {
        recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
    }

    private void nestedEthCallsContractPersist() {
        final var nestedEthCallsContractAddress = toAddress(EntityId.of(0, 0, 1262));
        final var contractBytes = functionEncodeDecoder.getContractBytes(NESTED_CALLS_CONTRACT_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(nestedEthCallsContractAddress.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(contractEntityId.getId())
                        .num(contractEntityId.getNum())
                        .evmAddress(contractEvmAddress)
                        .type(CONTRACT)
                        .key(Key.newBuilder()
                                .setEd25519(ByteString.copyFrom(Arrays.copyOfRange(KEY_PROTO, 3, KEY_PROTO.length)))
                                .build()
                                .toByteArray())
                        .balance(1500L)
                        .timestampRange(Range.closedOpen(
                                recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd())))
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

        recordFileForBlockHash = domainBuilder
                .recordFile()
                .customize(f -> f.bytes(contractBytes))
                .persist();
    }
}
