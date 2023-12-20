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

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContractCallEvmCodesTest extends ContractCallTestSetup {

    private static final String TRUE = "0x0000000000000000000000000000000000000000000000000000000000000001";

    @Autowired
    private RecordFileRepository recordFileRepository;

    private boolean areEntitiesPersisted;

    @Test
    void chainId() {
        final var functionHash = functionEncodeDecoder.functionHashFor("chainId", EVM_CODES_ABI_PATH);
        final var serviceParameters = serviceParametersForEvmCodes(functionHash);

        assertThat(contractCallService.processCall(serviceParameters))
                .isEqualTo(mirrorNodeEvmProperties.chainIdBytes32().toHexString());
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

    private CallServiceParameters serviceParametersForEvmCodes(final Bytes callData) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        if (!areEntitiesPersisted) {
            persistEntities();
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
}
