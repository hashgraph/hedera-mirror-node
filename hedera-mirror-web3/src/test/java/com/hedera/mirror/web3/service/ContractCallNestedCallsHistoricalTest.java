/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.KEY_PROTO;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.NestedCallsHistorical;
import java.math.BigInteger;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractCallNestedCallsHistoricalTest extends AbstractContractCallServiceOpcodeTracerTest {

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
        final var ownerEntity = accountEntityNoEvmAddressPersistHistorical(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var spenderEntity = accountEntityPersistHistorical(testWeb3jService.getHistoricalRange());
        final var nftAmountToMint = 2;
        final var nft = nftPersistHistorical(
                nftAmountToMint,
                ownerEntity.toEntityId(),
                spenderEntity.toEntityId(),
                ownerEntity.toEntityId(),
                testWeb3jService.getHistoricalRange());

        final var contract = testWeb3jService.deploy(NestedCallsHistorical::deploy);

        // When
        final var function = contract.call_nestedGetTokenInfo(getAddressFromEntity(nft));
        final var result = function.send();
        // Then
        assertThat(result).isNotNull();
        assertThat(result.token).isNotNull();
        assertThat(result.deleted).isFalse();
        assertThat(result.token.memo).isEqualTo(nft.getMemo());
        verifyOpcodeTracerCall(function.encodeFunctionCall(), contract);
    }

    @Test
    void testGetApprovedHistorical() throws Exception {
        // When
        final var ownerEntity = accountEntityNoEvmAddressPersistHistorical(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));

        final var spenderEntity = accountEntityPersistHistorical(testWeb3jService.getHistoricalRange());
        final var nftAmountToMint = 2;
        final var nft = nftPersistHistorical(
                nftAmountToMint,
                ownerEntity.toEntityId(),
                spenderEntity.toEntityId(),
                ownerEntity.toEntityId(),
                testWeb3jService.getHistoricalRange());
        final var contract = testWeb3jService.deploy(NestedCallsHistorical::deploy);

        // When
        final var function = contract.call_nestedHtsGetApproved(getAddressFromEntity(nft), BigInteger.ONE);
        final var result = function.send();

        // Then
        final var expectedOutput = getAliasFromEntity(spenderEntity);
        assertThat(result).isEqualTo(expectedOutput);
        verifyOpcodeTracerCall(function.encodeFunctionCall(), contract);
    }

    @Test
    void testMintTokenHistorical() throws Exception {
        // Given
        final var ownerEntity = accountEntityNoEvmAddressPersistHistorical(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        final var spenderEntity = accountEntityPersistHistorical(testWeb3jService.getHistoricalRange());
        final var nftAmountToMint = 3;
        final var nft = nftPersistHistorical(
                nftAmountToMint,
                ownerEntity.toEntityId(),
                spenderEntity.toEntityId(),
                ownerEntity.toEntityId(),
                testWeb3jService.getHistoricalRange());

        domainBuilder
                .tokenAccountHistory()
                .customize(e -> e.freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .accountId(ownerEntity.getId())
                        .tokenId(nft.getId())
                        .timestampRange(testWeb3jService.getHistoricalRange()))
                .persist();

        final var contract = testWeb3jService.deploy(NestedCallsHistorical::deploy);

        // When
        final var function = contract.call_nestedMintToken(
                getAddressFromEntity(nft),
                BigInteger.ZERO,
                Collections.singletonList(ByteString.copyFromUtf8("firstMeta").toByteArray()));
        final var result = function.send();

        // Then
        int expectedTotalSupply = nftAmountToMint + 1;
        assertThat(result).isEqualTo(BigInteger.valueOf(expectedTotalSupply));
        verifyOpcodeTracerCall(function.encodeFunctionCall(), contract);
    }

    private Entity nftPersistHistorical(
            final int nftAmountToMint,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final EntityId treasuryId,
            final Range<Long> historicalBlock) {

        final var ownerEntity = EntityId.of(ownerEntityId.getId());

        final var nftEntity = domainBuilder
                .entity()
                .customize(e -> e.type(TOKEN).key(KEY_PROTO).deleted(false).timestampRange(historicalBlock))
                .persist();

        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .treasuryAccountId(treasuryId)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycKey(KEY_PROTO)
                        .freezeDefault(true)
                        .feeScheduleKey(KEY_PROTO)
                        .maxSupply(2_000_000_000L)
                        .supplyType(TokenSupplyTypeEnum.FINITE)
                        .freezeKey(KEY_PROTO)
                        .pauseKey(KEY_PROTO)
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
                            .createdTimestamp(historicalBlock.lowerEndpoint() - 1)
                            .serialNumber(finalI + 1)
                            .spender(spenderEntityId)
                            .metadata("NFT_METADATA_URI".getBytes())
                            .accountId(ownerEntity)
                            .tokenId(nftEntity.getId())
                            .deleted(false)
                            .timestampRange(Range.openClosed(
                                    historicalBlock.lowerEndpoint() - 1, historicalBlock.upperEndpoint() + 1)))
                    .persist();

            domainBuilder
                    .nft()
                    .customize(n -> n.accountId(spenderEntityId)
                            .createdTimestamp(historicalBlock.lowerEndpoint() - 1)
                            .serialNumber(finalI + 1)
                            .metadata("NFT_METADATA_URI".getBytes())
                            .accountId(ownerEntity)
                            .tokenId(nftEntity.getId())
                            .deleted(false)
                            .timestampRange(Range.atLeast(historicalBlock.upperEndpoint() + 1)))
                    .persist();
        }
        return nftEntity;
    }
}
