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
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.NestedCallsHistorical;
import java.math.BigInteger;
import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractCallNestedCallsHistoricalTest extends AbstractContractCallServiceTest {

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
        final var result = contract.call_nestedGetTokenInfoAndHardcodedResult(tokenAddress.toHexString())
                .send();
        // Then
        assertThat(result).isNotNull();
        assertThat(result.token).isNotNull();
        assertThat(result.deleted).isFalse();
        assertThat(result.token.memo).isEqualTo(tokenMemo);
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
        final var result = contract.call_nestedHtsGetApprovedAndHardcodedResult(
                        tokenAddress.toHexString(), BigInteger.ONE)
                .send();

        // Then
        final var key = ByteString.fromHex(spenderPublicKey);
        final var expectedOutput = Address.wrap(
                        Bytes.wrap(recoverAddressFromPubKey(key.substring(2).toByteArray())))
                .toString();
        assertThat(result).isEqualTo(expectedOutput);
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
        final var result = contract.call_nestedMintTokenAndHardcodedResult(
                        tokenAddress.toHexString(),
                        BigInteger.ZERO,
                        Collections.singletonList(
                                ByteString.copyFromUtf8("firstMeta").toByteArray()))
                .send();

        // Then
        int expectedTotalSupply = nftAmountToMint + 1;
        assertThat(result).isEqualTo(BigInteger.valueOf(expectedTotalSupply));
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
}
