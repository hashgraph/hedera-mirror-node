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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.NftAllowanceRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class SyntheticNftAllowanceOwnerMigrationTest extends IntegrationTest {

    private final SyntheticNftAllowanceOwnerMigration migration;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final String idColumns = "payer_account_id, spender, token_id";

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isOne();
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(nftAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(NftAllowance.class, idColumns)).isEmpty();
    }

    @Test
    void migrate() {
        // given
        // Nft allowance and nft allowance history entries that have the incorrect owner
        var contractResultSenderId = EntityId.of("0.0.2001", CONTRACT);
        long incorrectOwnerAccountId = 1001L;
        var incorrectNftAllowancePair = generateNftAllowance(contractResultSenderId, incorrectOwnerAccountId);

        // An nft allowance can occur through HAPI and absent a corresponding Contract Result.
        // This nft allowance and it's history should be unaffected by the migration
        var unaffectedNftAllowancePair = generateNftAllowance(null, null);

        // This problem has already been fixed. So there are nft allowances with the correct owner.
        // This nft allowance and it's history should be unaffected by the migration
        var correctContractResultSenderId = EntityId.of("0.0.3001", CONTRACT);
        var correctNftAllowancePair =
                generateNftAllowance(correctContractResultSenderId, correctContractResultSenderId.getId());

        // A nft allowance that has no history and the correct owner, it should not be affected by the migration.
        var newNftAllowance = domainBuilder.nftAllowance().persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(new EntityId(0L, 0L, newNftAllowance.getOwner(), CONTRACT))
                        .consensusTimestamp(newNftAllowance.getTimestampLower()))
                .persist();

        // An nft allowance that has no contract result, but has primary key values that will end up matching those of
        // a migrated nft allowance.
        var ownerAccountId = 38L;
        var spender = 39L;
        var tokenId = 44L;
        var nftAllowancePreMigration = domainBuilder
                .nftAllowance()
                .customize(t -> t.owner(ownerAccountId)
                        .payerAccountId(EntityId.of(ownerAccountId, CONTRACT))
                        .spender(spender)
                        .timestampRange(Range.atLeast(1676546171829734003L))
                        .tokenId(tokenId))
                .persist();

        // An nft allowance that has a contract result, once migrated it will have the same primary key fields as the
        // above nft allowance.
        var ownerPreMigration = 1322L;
        var contractResultConsensus = 1676546391434923004L;
        var collidedNftAllowance = domainBuilder
                .nftAllowance()
                .customize(t -> t.owner(ownerPreMigration)
                        .payerAccountId(EntityId.of(ownerPreMigration, CONTRACT))
                        .spender(spender)
                        .timestampRange(Range.atLeast(contractResultConsensus))
                        .tokenId(tokenId))
                .persist();

        // The contract result for the collided nft allowance
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(EntityId.of(ownerAccountId, CONTRACT))
                        .payerAccountId(EntityId.of(ownerPreMigration, CONTRACT))
                        .consensusTimestamp(contractResultConsensus))
                .persist();

        // when
        migration.doMigrate();

        // then
        var expected = new ArrayList<>(List.of(incorrectNftAllowancePair.getLeft()));
        // The owner should be set to the contract result sender id
        expected.forEach(t -> t.setOwner(contractResultSenderId.getId()));
        expected.add(unaffectedNftAllowancePair.getLeft());
        expected.add(correctNftAllowancePair.getLeft());
        expected.add(newNftAllowance);
        collidedNftAllowance.setOwner(ownerAccountId);
        expected.add(collidedNftAllowance);
        assertThat(nftAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        // The history of the nft allowance should also be updated with the corrected owner
        var expectedHistory = new ArrayList<>(incorrectNftAllowancePair.getRight());
        // The owner should be set to the contract result sender id
        expectedHistory.forEach(t -> t.setOwner(contractResultSenderId.getId()));
        expectedHistory.addAll(unaffectedNftAllowancePair.getRight());
        expectedHistory.addAll(correctNftAllowancePair.getRight());
        nftAllowancePreMigration.setTimestampUpper(contractResultConsensus);
        expectedHistory.add(nftAllowancePreMigration);
        assertThat(findHistory(NftAllowance.class, idColumns)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    @Test
    void repeatableMigration() {
        // given
        var contractResultSenderId = EntityId.of("0.0.2001", CONTRACT);
        long incorrectOwnerAccountId = 1001L;
        generateNftAllowance(contractResultSenderId, incorrectOwnerAccountId);
        generateNftAllowance(null, null);
        var correctContractResultSenderId = EntityId.of("0.0.3001", CONTRACT);
        generateNftAllowance(correctContractResultSenderId, correctContractResultSenderId.getId());
        var newNftAllowance = domainBuilder.nftAllowance().persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(new EntityId(0L, 0L, newNftAllowance.getOwner(), CONTRACT))
                        .consensusTimestamp(newNftAllowance.getTimestampLower()))
                .persist();

        // The collision nft allowances
        var ownerAccountId = 3491438L;
        var spender = 3491439L;
        var tokenId = 3491444L;
        domainBuilder
                .nftAllowance()
                .customize(t -> t.owner(ownerAccountId)
                        .payerAccountId(EntityId.of(ownerAccountId, CONTRACT))
                        .spender(spender)
                        .timestampRange(Range.atLeast(1676546171829734003L))
                        .tokenId(tokenId))
                .persist();

        var ownerPreMigration = 1322L;
        var contractResultConsensus = 1676546391434923004L;
        domainBuilder
                .nftAllowance()
                .customize(t -> t.owner(ownerPreMigration)
                        .payerAccountId(EntityId.of(ownerPreMigration, CONTRACT))
                        .spender(spender)
                        .timestampRange(Range.atLeast(contractResultConsensus))
                        .tokenId(tokenId))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(EntityId.of(ownerAccountId, CONTRACT))
                        .payerAccountId(EntityId.of(ownerPreMigration, CONTRACT))
                        .consensusTimestamp(contractResultConsensus))
                .persist();

        migration.doMigrate();
        var firstPassNftAllowances = nftAllowanceRepository.findAll();
        var firstPassHistory = findHistory(NftAllowance.class, idColumns);

        // when
        migration.doMigrate();
        var secondPassNftAllowances = nftAllowanceRepository.findAll();
        var secondPassHistory = findHistory(NftAllowance.class, idColumns);

        // then
        assertThat(firstPassNftAllowances).containsExactlyInAnyOrderElementsOf(secondPassNftAllowances);
        assertThat(firstPassHistory).containsExactlyInAnyOrderElementsOf(secondPassHistory);
    }

    /**
     * Creates an nft allowance and two historical nft allowances to populate nft_allowance and
     * nft_allowance_history
     *
     * @param contractResultSenderId
     * @param ownerAccountId
     * @return the current nft allowance and the historical nft allowances
     */
    private Pair<NftAllowance, List<NftAllowance>> generateNftAllowance(
            EntityId contractResultSenderId, Long ownerAccountId) {
        var builder = domainBuilder.nftAllowance();
        if (contractResultSenderId != null) {
            builder.customize(nfta -> nfta.owner(ownerAccountId));
        }
        var currentNftAllowance = builder.persist();
        var range = currentNftAllowance.getTimestampRange();
        var rangeUpdate1 = Range.closedOpen(range.lowerEndpoint() - 2, range.lowerEndpoint() - 1);
        var rangeUpdate2 = Range.closedOpen(range.lowerEndpoint() - 1, range.lowerEndpoint());

        var update1 = builder.customize(nfta -> nfta.approvedForAll(false).timestampRange(rangeUpdate1))
                .get();
        var update2 = builder.customize(nfta -> nfta.approvedForAll(true).timestampRange(rangeUpdate2))
                .get();
        saveHistory(update1);
        saveHistory(update2);

        if (contractResultSenderId != null) {
            var contractResult = domainBuilder.contractResult();
            for (var nftAllowance : List.of(currentNftAllowance, update1, update2)) {
                contractResult
                        .customize(c ->
                                c.senderId(contractResultSenderId).consensusTimestamp(nftAllowance.getTimestampLower()))
                        .persist();
            }
        }

        return Pair.of(currentNftAllowance, List.of(update1, update2));
    }

    private void saveHistory(NftAllowance nftAllowance) {
        domainBuilder
                .nftAllowanceHistory()
                .customize(c -> c.approvedForAll(nftAllowance.isApprovedForAll())
                        .owner(nftAllowance.getOwner())
                        .payerAccountId(nftAllowance.getPayerAccountId())
                        .spender(nftAllowance.getSpender())
                        .timestampRange(nftAllowance.getTimestampRange())
                        .tokenId(nftAllowance.getTokenId()))
                .persist();
    }
}
