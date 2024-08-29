/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.KEY_PROTO;
import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.OPTIONS;
import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.gasComparator;
import static com.hedera.mirror.web3.utils.OpcodeTracerUtil.toHumanReadableMessage;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.convert.BytesDecoder;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderRecord;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.NestedCallsHistorical;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import java.math.BigInteger;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.web3j.tx.Contract;

@RequiredArgsConstructor
class ContractCallNestedCallsHistoricalTest extends AbstractContractCallServiceTest {

    private final ContractDebugService contractDebugService;

    @Captor
    private ArgumentCaptor<ContractDebugParameters> paramsCaptor;

    @Captor
    private ArgumentCaptor<Long> gasCaptor;

    private HederaEvmTransactionProcessingResult resultCaptor;
    private ContractCallContext contextCaptor;

    private RecordFile recordFileBeforeEvm34;

    @BeforeEach
    void beforeEach() {
        recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));

        doAnswer(invocation -> {
                    final var transactionProcessingResult =
                            (HederaEvmTransactionProcessingResult) invocation.callRealMethod();
                    resultCaptor = transactionProcessingResult;
                    contextCaptor = ContractCallContext.get();
                    return transactionProcessingResult;
                })
                .when(processor)
                .execute(paramsCaptor.capture(), gasCaptor.capture());
    }

    @Test
    void testGetHistoricalInfo() throws Exception {
        // Given
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistorical(ownerAddress);
        final var spenderAddress = toAddress(1016);
        final var spenderPublicKeyHistorical = "3a210398e17bcbd2926c4d8a31e32616b4754ac0a2fc71d7fb768e657db46202625f34";
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress, spenderPublicKeyHistorical);
        final var tokenAddress = toAddress(1063);
        final var tokenMemo = "TestMemo";
        final var nftAmountToMint = 2;
        nftPersistHistorical(
                tokenAddress,
                tokenMemo,
                nftAmountToMint,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));

        final var contract = testWeb3jService.deploy(NestedCallsHistorical::deploy);

        // When
        final var function = contract.call_nestedGetTokenInfo(tokenAddress.toHexString());
        final var result = function.send();
        // Then
        assertThat(result).isNotNull();
        assertThat(result.token).isNotNull();
        assertThat(result.deleted).isFalse();
        assertThat(result.token.memo).isEqualTo(tokenMemo);
        verifyOpcodeTracerCall(function.encodeFunctionCall(), contract);
    }

    @Test
    void testGetApprovedHistorical() throws Exception {
        // When
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistorical(ownerAddress);
        final var spenderAddress = toAddress(1016);
        final var spenderPublicKey = "3a210398e17bcbd2926c4d8a31e32616b4754ac0a2fc71d7fb768e657db46202625f34";
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress, spenderPublicKey);
        final var tokenAddress = toAddress(1063);
        final var tokenMemo = "TestMemo1";
        final var nftAmountToMint = 2;
        nftPersistHistorical(
                tokenAddress,
                tokenMemo,
                nftAmountToMint,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(NestedCallsHistorical::deploy);

        // When
        final var function = contract.call_nestedHtsGetApproved(tokenAddress.toHexString(), BigInteger.ONE);
        final var result = function.send();

        // Then
        final var key = ByteString.fromHex(spenderPublicKey);
        final var expectedOutput = Address.wrap(
                        Bytes.wrap(recoverAddressFromPubKey(key.substring(2).toByteArray())))
                .toString();
        assertThat(result).isEqualTo(expectedOutput);
        verifyOpcodeTracerCall(function.encodeFunctionCall(), contract);
    }

    @Test
    void testMintTokenHistorical() throws Exception {
        // Given
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistorical(ownerAddress);
        final var spenderAddress = toAddress(1016);
        final var spenderPublicKeyHistorical = "3a210398e17bcbd2926c4d8a31e32616b4754ac0a2fc71d7fb768e657db46202625f34";
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress, spenderPublicKeyHistorical);
        final var tokenAddress = toAddress(1063);
        final var tokenMemo = "TestMemo2";
        final var nftAmountToMint = 3;
        final var nftEntity = nftPersistHistorical(
                tokenAddress,
                tokenMemo,
                nftAmountToMint,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));

        domainBuilder
                .tokenAccountHistory()
                .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .accountId(ownerEntityId.getId())
                        .tokenId(nftEntity.getId())
                        .timestampRange(Range.closedOpen(
                                recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd())))
                .persist();

        final var contract = testWeb3jService.deploy(NestedCallsHistorical::deploy);

        // When
        final var function = contract.call_nestedMintToken(
                tokenAddress.toHexString(),
                BigInteger.ZERO,
                Collections.singletonList(ByteString.copyFromUtf8("firstMeta").toByteArray()));
        final var result = function.send();

        // Then
        int expectedTotalSupply = nftAmountToMint + 1;
        assertThat(result).isEqualTo(BigInteger.valueOf(expectedTotalSupply));
        verifyOpcodeTracerCall(function.encodeFunctionCall(), contract);
    }

    private EntityId nftPersistHistorical(
            final Address tokenAddress,
            final String memo,
            final int nftAmountToMint,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final EntityId treasuryId,
            final Range<Long> historicalBlock) {

        final var nftEntityId = entityIdFromEvmAddress(tokenAddress);
        final var nftEvmAddress = toEvmAddress(nftEntityId);
        final var ownerEntity = EntityId.of(ownerEntityId.getId());

        domainBuilder
                .entity()
                .customize(e -> e.id(nftEntityId.getId())
                        .num(nftEntityId.getNum())
                        .evmAddress(nftEvmAddress)
                        .type(TOKEN)
                        .key(KEY_PROTO)
                        .expirationTimestamp(9999999999999L)
                        .memo(memo)
                        .deleted(false)
                        .timestampRange(historicalBlock))
                .persist();

        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(nftEntityId.getId())
                        .treasuryAccountId(treasuryId)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycKey(KEY_PROTO)
                        .freezeDefault(true)
                        .feeScheduleKey(KEY_PROTO)
                        .maxSupply(2_000_000_000L)
                        .supplyType(TokenSupplyTypeEnum.FINITE)
                        .freezeKey(KEY_PROTO)
                        .pauseKey(KEY_PROTO)
                        .pauseStatus(TokenPauseStatusEnum.PAUSED)
                        .wipeKey(KEY_PROTO)
                        .supplyKey(KEY_PROTO)
                        .wipeKey(KEY_PROTO)
                        .decimals(0)
                        .timestampRange(historicalBlock))
                .persist();

        for (int i = 0; i < nftAmountToMint; i++) {
            int finalI = i;
            domainBuilder
                    .nftHistory()
                    .customize(n -> n.accountId(spenderEntityId)
                            .createdTimestamp(1475067194949034022L)
                            .serialNumber(finalI + 1)
                            .spender(spenderEntityId)
                            .metadata("NFT_METADATA_URI".getBytes())
                            .accountId(ownerEntity)
                            .tokenId(nftEntityId.getId())
                            .deleted(false)
                            .timestampRange(Range.openClosed(
                                    historicalBlock.lowerEndpoint() - 1, historicalBlock.upperEndpoint() + 1)))
                    .persist();

            domainBuilder
                    .nft()
                    .customize(n -> n.accountId(spenderEntityId)
                            .createdTimestamp(1475067194949034022L)
                            .serialNumber(finalI + 1)
                            .metadata("NFT_METADATA_URI".getBytes())
                            .accountId(ownerEntity)
                            .tokenId(nftEntityId.getId())
                            .deleted(false)
                            .timestampRange(Range.atLeast(historicalBlock.upperEndpoint() + 1)))
                    .persist();
        }
        return nftEntityId;
    }

    private EntityId ownerEntityPersistHistorical(Address address) {
        final var ownerEntityId = entityIdFromEvmAddress(address);
        domainBuilder
                .entity()
                .customize(e -> e.id(ownerEntityId.getId())
                        .num(ownerEntityId.getNum())
                        .alias(toEvmAddress(ownerEntityId))
                        .timestampRange(Range.closedOpen(
                                recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd())))
                .persist();
        return ownerEntityId;
    }

    private EntityId spenderEntityPersistHistorical(Address spenderAddress, String spenderAlias) {
        final var spenderEntityId = entityIdFromEvmAddress(spenderAddress);
        final var spenderPublicKeyHistorical = ByteString.fromHex(spenderAlias);
        final var spenderAliasHistorical = Address.wrap(Bytes.wrap(
                recoverAddressFromPubKey(spenderPublicKeyHistorical.substring(2).toByteArray())));
        domainBuilder
                .entity()
                .customize(e -> e.id(spenderEntityId.getId())
                        .num(spenderEntityId.getNum())
                        .evmAddress(spenderAliasHistorical.toArray())
                        .alias(spenderPublicKeyHistorical.toByteArray())
                        .deleted(false)
                        .createdTimestamp(recordFileBeforeEvm34.getConsensusStart())
                        .timestampRange(Range.closedOpen(
                                recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd())))
                .persist();
        return spenderEntityId;
    }

    private void verifyOpcodeTracerCall(final String callData, final Contract contract) {
        ContractFunctionProviderRecord functionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .build();

        final var callDataBytes = Bytes.fromHexString(callData);
        final var debugParameters = ContractDebugParameters.builder()
                .block(functionProvider.block())
                .callData(callDataBytes)
                .consensusTimestamp(domainBuilder.timestamp())
                .gas(15_000_000L)
                .receiver(functionProvider.contractAddress())
                .sender(new HederaEvmAccount(functionProvider.sender()))
                .value(functionProvider.value())
                .build();

        if (functionProvider.expectedErrorMessage() != null) {
            verifyThrowingOpcodeTracerCall(debugParameters, functionProvider);
        } else {
            verifySuccessfulOpcodeTracerCall(debugParameters);
        }
        assertThat(paramsCaptor.getValue()).isEqualTo(debugParameters);
        assertThat(gasCaptor.getValue()).isEqualTo(debugParameters.getGas());
    }

    @SneakyThrows
    private void verifyThrowingOpcodeTracerCall(
            final ContractDebugParameters params, final ContractFunctionProviderRecord function) {
        final var actual = contractDebugService.processOpcodeCall(params, OPTIONS);
        assertThat(actual.transactionProcessingResult().isSuccessful()).isFalse();
        assertThat(actual.transactionProcessingResult().getOutput()).isEqualTo(Bytes.EMPTY);
        assertThat(actual.transactionProcessingResult())
                .satisfiesAnyOf(
                        result -> assertThat(result.getRevertReason())
                                .isPresent()
                                .map(BytesDecoder::maybeDecodeSolidityErrorStringToReadableMessage)
                                .hasValue(function.expectedErrorMessage()),
                        result -> assertThat(result.getHaltReason())
                                .isPresent()
                                .map(ExceptionalHaltReason::getDescription)
                                .hasValue(function.expectedErrorMessage()));
        assertThat(actual.opcodes().size()).isNotZero();
        assertThat(toHumanReadableMessage(actual.opcodes().getLast().reason()))
                .isEqualTo(function.expectedErrorMessage());
    }

    private void verifySuccessfulOpcodeTracerCall(final ContractDebugParameters params) {
        final var actual = contractDebugService.processOpcodeCall(params, OPTIONS);
        final var expected = new OpcodesProcessingResult(resultCaptor, contextCaptor.getOpcodes());
        // Compare transaction processing result
        assertThat(actual.transactionProcessingResult())
                .usingRecursiveComparison()
                .ignoringFields("logs")
                .isEqualTo(expected.transactionProcessingResult());
        // Compare opcodes with gas tolerance
        assertThat(actual.opcodes())
                .usingRecursiveComparison()
                .withComparatorForFields(gasComparator(), "gas")
                .isEqualTo(expected.opcodes());
    }
}
