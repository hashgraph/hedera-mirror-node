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

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.SENDER_ADDRESS_HISTORICAL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.KEY_PROTO;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.utils.ContractFunctionProviderEnum;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.ERCTestContractHistorical;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ContractCallServiceERCTokenHistoricalTest extends AbstractContractCallServiceTest {

    public static final String REDIRECT_SUFFIX = "Redirect";
    public static final String NON_STATIC_SUFFIX = "NonStatic";
    private RecordFile recordFileBeforeEvm34;
    protected static RecordFile recordFileAfterEvm34;

//    private static Stream<Arguments> ercContractFunctionArgumentsProviderHistoricalReadOnly() {
//        List<BlockType> blockNumbers =
//                List.of(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)), BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
//
//        return Arrays.stream(ErcContractReadOnlyFunctionsHistorical.values())
//                .flatMap(ercFunction -> Stream.concat(
//                        blockNumbers.stream().map(blockNumber -> Arguments.of(ercFunction, true, blockNumber)),
//                        blockNumbers.stream().map(blockNumber -> Arguments.of(ercFunction, false, blockNumber))));
//    }
//
//    @ParameterizedTest
//    @MethodSource("ercContractFunctionArgumentsProviderHistoricalReadOnly")
//    void ercReadOnlyPrecompileHistoricalOperationsTest(
//            final ErcContractReadOnlyFunctionsHistorical ercFunction,
//            final boolean isStatic,
//            final BlockType blockNumber) {
//        final var functionName = ercFunction.getName(isStatic);
//        final var functionHash =
//                functionEncodeDecoder.functionHashFor(functionName, ERC_ABI_PATH, ercFunction.functionParameters);
//        final var serviceParameters =
//                serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L, blockNumber);
//
//        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
//                ercFunction.name, ERC_ABI_PATH, ercFunction.expectedResultFields);
//
//        // Before the block the data did not exist yet
//        if (blockNumber.number() < EVM_V_34_BLOCK) {
//            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
//                    .isInstanceOf(MirrorEvmTransactionException.class);
//        } else {
//            assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
//        }
//    }
//
//    @ParameterizedTest
//    @ValueSource(longs = {51, Long.MAX_VALUE - 1})
//    void ercReadOnlyPrecompileHistoricalNotExistingBlockTest(final long blockNumber) {
//        final var functionHash = functionEncodeDecoder.functionHashFor(
//                "isApprovedForAll",
//                ERC_ABI_PATH,
//                NFT_ADDRESS_HISTORICAL,
//                SENDER_ADDRESS_HISTORICAL,
//                SPENDER_ADDRESS_HISTORICAL);
//        final var serviceParameters = serviceParametersForExecution(
//                functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.of(String.valueOf(blockNumber)));
//        final var latestBlockNumber = recordFileRepository.findLatestIndex().orElse(Long.MAX_VALUE);
//
//        // Block number (Long.MAX_VALUE - 1) does not exist in the DB and is after the
//        // latest block available in the DB => returning error
//        if (blockNumber > latestBlockNumber) {
//            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
//                    .isInstanceOf(BlockNumberOutOfRangeException.class);
//        } else if (blockNumber == 51) {
//            // Block number 51 = (EVM_V_34_BLOCK + 1) does not exist in the DB but it is before the latest
//            // block available in the DB => throw an exception
//            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
//                    .isInstanceOf(BlockNumberNotFoundException.class);
//        }
//    }
//
//    @ParameterizedTest
//    @EnumSource(ErcContractReadOnlyFunctionsNegative.class)
//    void supportedErcReadOnlyRedirectPrecompileNegativeOperationsTest(
//            final ErcContractReadOnlyFunctionsNegative ercFunction) {
//        final var functionName = ercFunction.name + REDIRECT_SUFFIX;
//        final var functionHash = functionEncodeDecoder.functionHashFor(
//                functionName, REDIRECT_CONTRACT_ABI_PATH, ercFunction.functionParameters);
//        final var serviceParameters = serviceParametersForExecution(
//                functionHash, REDIRECT_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);
//
//        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
//                .isInstanceOf(MirrorEvmTransactionException.class);
//    }
//
//    @Getter
//    public enum ErcContractReadOnlyFunctionsNegative implements ContractFunctionProviderEnum {
//        // Negative scenarios - expected to throw an exception
//        ERC_DECIMALS_NEGATIVE("decimals", new Address[] {NFT_ADDRESS}),
//        OWNER_OF_NEGATIVE("getOwnerOf", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 1L}),
//        TOKEN_URI_NEGATIVE("tokenURI", new Object[] {FUNGIBLE_TOKEN_ADDRESS, 1L});
//
//        private final String name;
//        private final Object[] functionParameters;
//    }
//
//    @Getter
//    public enum ErcContractReadOnlyFunctionsHistorical implements ContractFunctionProviderEnum {
//        GET_APPROVED_EMPTY_SPENDER(
//                "getApproved", new Object[] {NFT_ADDRESS_HISTORICAL, 2L}, new Address[] {Address.ZERO}),
//        IS_APPROVE_FOR_ALL(
//                "isApprovedForAll",
//                new Address[] {NFT_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL, SPENDER_ADDRESS_HISTORICAL},
//                new Boolean[] {true}),
//        IS_APPROVE_FOR_ALL_WITH_ALIAS(
//                "isApprovedForAll",
//                new Address[] {NFT_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL, SPENDER_ALIAS_HISTORICAL},
//                new Boolean[] {true}),
//        ALLOWANCE_OF(
//                "allowance",
//                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL, SPENDER_ADDRESS_HISTORICAL
//                },
//                new Long[] {13L}),
//        ALLOWANCE_OF_WITH_ALIAS(
//                "allowance",
//                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL, SPENDER_ALIAS_HISTORICAL},
//                new Long[] {13L}),
//        GET_APPROVED(
//                "getApproved", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new Address[] {SPENDER_ALIAS_HISTORICAL}),
//        ERC_DECIMALS("decimals", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Integer[] {12}),
//        TOTAL_SUPPLY("totalSupply", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new Long[] {12345L}),
//        ERC_SYMBOL("symbol", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new String[] {"HBAR"}),
//        BALANCE_OF(
//                "balanceOf",
//                new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ADDRESS_HISTORICAL},
//                new Long[] {12L}),
//        BALANCE_OF_WITH_ALIAS(
//                "balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL, SENDER_ALIAS_HISTORICAL}, new Long[] {12L
//                }),
//        ERC_NAME("name", new Address[] {FUNGIBLE_TOKEN_ADDRESS_HISTORICAL}, new String[] {"Hbars"}),
//        OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new Address[] {OWNER_ADDRESS_HISTORICAL}),
//        EMPTY_OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS_HISTORICAL, 2L}, new Address[] {Address.ZERO}),
//        TOKEN_URI("tokenURI", new Object[] {NFT_ADDRESS_HISTORICAL, 1L}, new String[] {"NFT_METADATA_URI"});
//
//        private final String name;
//        private final Object[] functionParameters;
//        private final Object[] expectedResultFields;
//
//        public String getName(final boolean isStatic) {
//            return isStatic ? name : name + NON_STATIC_SUFFIX;
//        }
//    }

    @Test
    void getApprovedEmptySpenderBeforeEvmV34() throws Exception {
        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress = toAddress(1063);
        final var ownerAddress = toAddress(1065);
        final var ownerEntityId = ownerEntityPersistHistorical(ownerAddress);
        final var spenderAddress = toAddress(1016);
        final var autoRenewAddress = toAddress(1078);
        final var spenderPublicKeyHistorical = "3a210398e17bcbd2926c4d8a31e32616b4754ac0a2fc71d7fb768e657db46202625f34";
        final var spenderEntityPersist = spenderEntityPersistHistorical(spenderAddress, spenderPublicKeyHistorical);
//        final var tokenMemo = "TestMemo";
//        final var nftAmountToMint = 2;
        final var nftEntity = nftPersistHistorical(
                tokenAddress,
                autoRenewAddress,
                ownerEntityId,
                spenderEntityPersist,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true,
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
        final var tokenAddress3 = getAddressFromEntity(nftEntity.toEntity());
        final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
        final var result =
                contract.call_getApproved(tokenAddress3, BigInteger.valueOf(1L)).send();
        //assertThatThrownBy(result::send).isInstance Of(MirrorEvmTransactionException.class);

    }


    private void evmBeforeV34Block(){
        recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
    }

    private void evmAfterV34Block(){
        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
    }

    private EntityId ownerEntityPersistHistorical(Address address) {
        final var ownerEntityId = entityIdFromEvmAddress(address);
        domainBuilder
                .entity()
                .customize(e -> e.id(ownerEntityId.getId())
                        .num(ownerEntityId.getNum())
                        .alias(toEvmAddress(ownerEntityId))
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
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
                        .createdTimestamp(recordFileAfterEvm34.getConsensusStart())
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();
        return spenderEntityId;
    }

    private EntityId nftPersistHistorical(
            final Address nftAddress,
            final Address autoRenewAddress,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final EntityId treasuryId,
            final byte[] key,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault,
            final Range<Long> historicalBlock) {
        final var nftEntityId = entityIdFromEvmAddress(nftAddress);
        final var autoRenewEntityId = entityIdFromEvmAddress(autoRenewAddress);
        final var nftEvmAddress = toEvmAddress(nftEntityId);
        final var ownerEntity = EntityId.of(ownerEntityId.getId());

        domainBuilder
                .entity()
                .customize(e -> e.id(nftEntityId.getId())
                        .autoRenewAccountId(autoRenewEntityId.getId())
                        .num(nftEntityId.getNum())
                        .evmAddress(nftEvmAddress)
                        .type(TOKEN)
                        .balance(1500L)
                        .key(key)
                        .expirationTimestamp(9999999999999L)
                        .memo("TestMemo")
                        .deleted(false)
                        .timestampRange(historicalBlock))
                .persist();

        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(nftEntityId.getId())
                        .treasuryAccountId(treasuryId)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycKey(key)
                        .freezeDefault(freezeDefault)
                        .feeScheduleKey(key)
                        .totalSupply(2L)
                        .maxSupply(2_000_000_000L)
                        .name("Hbars")
                        .supplyType(TokenSupplyTypeEnum.FINITE)
                        .freezeKey(key)
                        .pauseKey(key)
                        .pauseStatus(pauseStatus)
                        .wipeKey(key)
                        .supplyKey(key)
                        .symbol("HBAR")
                        .wipeKey(key)
                        .decimals(0)
                        .timestampRange(historicalBlock))
                .persist();

        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(1L)
                        .spender(spenderEntityId)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
                        .tokenId(nftEntityId.getId())
                        .deleted(false)
                        .timestampRange(
                                Range.openClosed(historicalBlock.lowerEndpoint(), historicalBlock.upperEndpoint() + 1)))
                .persist();

        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(3L)
                        .spender(spenderEntityId)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
                        .tokenId(nftEntityId.getId())
                        .deleted(false)
                        .timestampRange(Range.openClosed(
                                historicalBlock.lowerEndpoint() - 1, historicalBlock.upperEndpoint() + 1)))
                .persist();

        // nft table
        domainBuilder
                .nft()
                .customize(n -> n.accountId(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(1L)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
                        .tokenId(nftEntityId.getId())
                        .deleted(false)
                        .timestampRange(Range.atLeast(historicalBlock.upperEndpoint() + 1)))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(3L)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
                        .tokenId(nftEntityId.getId())
                        .deleted(false)
                        .timestampRange(Range.atLeast(historicalBlock.upperEndpoint() + 1)))
                .persist();

        return nftEntityId;
    }
    private String getAddressFromEntity(Entity entity) {
        return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getId()));
    }


}
