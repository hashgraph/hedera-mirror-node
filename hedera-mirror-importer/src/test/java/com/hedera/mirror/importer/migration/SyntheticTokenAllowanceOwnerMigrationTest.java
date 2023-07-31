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
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.TokenAllowanceRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class SyntheticTokenAllowanceOwnerMigrationTest extends IntegrationTest {

    private final SyntheticTokenAllowanceOwnerMigration migration;
    private final TokenAllowanceRepository tokenAllowanceRepository;

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isEqualTo(2);
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(tokenAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(TokenAllowance.class)).isEmpty();
    }

    @Test
    void migrate() {
        // given
        // Token allowance and token allowance history entries that have the incorrect owner
        var contractResultSenderId = EntityId.of("0.0.2001", CONTRACT);
        long incorrectOwnerAccountId = 1001L;
        var incorrectTokenAllowancePair = generateTokenAllowance(contractResultSenderId, incorrectOwnerAccountId);

        // A token allowance can occur through HAPI and absent a corresponding Contract Result.
        // This token allowance and it's history should be unaffected by the migration
        var unaffectedTokenAllowancePair = generateTokenAllowance(null, null);

        // This problem has already been fixed. So there are token allowances with the correct owner.
        // This token allowance and it's history should be unaffected by the migration
        var correctContractResultSenderId = EntityId.of("0.0.3001", CONTRACT);
        var correctTokenAllowancePair =
                generateTokenAllowance(correctContractResultSenderId, correctContractResultSenderId.getId());

        // A token allowance that has no history and the correct owner, it should not be affected by the migration.
        var newTokenAllowance = domainBuilder.tokenAllowance().persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(new EntityId(0L, 0L, newTokenAllowance.getOwner(), CONTRACT))
                        .consensusTimestamp(newTokenAllowance.getTimestampLower()))
                .persist();

        // A token allowance that has no contract result, but has primary key values that will end up matching those of
        // a migrated token allowance.
        var ownerAccountId = 38L;
        var spender = 39L;
        var tokenId = 44L;
        var tokenAllowancePreMigration = domainBuilder
                .tokenAllowance()
                .customize(t -> t.owner(ownerAccountId)
                        .payerAccountId(EntityId.of(ownerAccountId, CONTRACT))
                        .spender(spender)
                        .timestampRange(Range.atLeast(1676546171829734003L))
                        .tokenId(tokenId))
                .persist();

        // A token allowance that has a contract result, once migrated it will have the same primary key fields as the
        // above token allowance.
        var ownerPreMigration = 1322L;
        var contractResultConsensus = 1676546391434923004L;
        var collidedTokenAllowance = domainBuilder
                .tokenAllowance()
                .customize(t -> t.owner(ownerPreMigration)
                        .payerAccountId(EntityId.of(ownerPreMigration, CONTRACT))
                        .spender(spender)
                        .timestampRange(Range.atLeast(contractResultConsensus))
                        .tokenId(tokenId))
                .persist();

        // The contract result for the collided token allowance
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(EntityId.of(ownerAccountId, CONTRACT))
                        .payerAccountId(EntityId.of(ownerPreMigration, CONTRACT))
                        .consensusTimestamp(contractResultConsensus))
                .persist();

        // when
        migration.doMigrate();

        // then
        var expected = new ArrayList<>(List.of(incorrectTokenAllowancePair.getLeft()));
        // The owner should be set to the contract result sender id
        expected.forEach(t -> t.setOwner(contractResultSenderId.getId()));
        expected.add(unaffectedTokenAllowancePair.getLeft());
        expected.add(correctTokenAllowancePair.getLeft());
        expected.add(newTokenAllowance);
        collidedTokenAllowance.setOwner(ownerAccountId);
        expected.add(collidedTokenAllowance);
        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        // The history of the token allowance should also be updated with the corrected owner
        var expectedHistory = new ArrayList<>(incorrectTokenAllowancePair.getRight());
        // The owner should be set to the contract result sender id
        expectedHistory.forEach(t -> t.setOwner(contractResultSenderId.getId()));
        expectedHistory.addAll(unaffectedTokenAllowancePair.getRight());
        expectedHistory.addAll(correctTokenAllowancePair.getRight());
        tokenAllowancePreMigration.setTimestampUpper(contractResultConsensus);
        expectedHistory.add(tokenAllowancePreMigration);
        assertThat(findHistory(TokenAllowance.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    @Test
    void repeatableMigration() {
        // given
        var contractResultSenderId = EntityId.of("0.0.2001", CONTRACT);
        long incorrectOwnerAccountId = 1001L;
        generateTokenAllowance(contractResultSenderId, incorrectOwnerAccountId);
        generateTokenAllowance(null, null);
        var correctContractResultSenderId = EntityId.of("0.0.3001", CONTRACT);
        generateTokenAllowance(correctContractResultSenderId, correctContractResultSenderId.getId());
        var newTokenAllowance = domainBuilder.tokenAllowance().persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(new EntityId(0L, 0L, newTokenAllowance.getOwner(), CONTRACT))
                        .consensusTimestamp(newTokenAllowance.getTimestampLower()))
                .persist();

        // The collision token allowances
        var ownerAccountId = 3491438L;
        var spender = 3491439L;
        var tokenId = 3491444L;
        domainBuilder
                .tokenAllowance()
                .customize(t -> t.owner(ownerAccountId)
                        .payerAccountId(EntityId.of(ownerAccountId, CONTRACT))
                        .spender(spender)
                        .timestampRange(Range.atLeast(1676546171829734003L))
                        .tokenId(tokenId))
                .persist();

        var ownerPreMigration = 1322L;
        var contractResultConsensus = 1676546391434923004L;
        domainBuilder
                .tokenAllowance()
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
        var firstPassTokenAllowances = tokenAllowanceRepository.findAll();
        var firstPassHistory = findHistory(TokenAllowance.class);

        // when
        migration.doMigrate();
        var secondPassTokenAllowances = tokenAllowanceRepository.findAll();
        var secondPassHistory = findHistory(TokenAllowance.class);

        // then
        assertThat(firstPassTokenAllowances).containsExactlyInAnyOrderElementsOf(secondPassTokenAllowances);
        assertThat(firstPassHistory).containsExactlyInAnyOrderElementsOf(secondPassHistory);
    }

    /**
     * Creates a token allowance and two historical token allowances to populate token_allowance and
     * token_allowance_history
     *
     * @param contractResultSenderId
     * @param ownerAccountId
     * @return the current token allowance and the historical token allowances
     */
    private Pair<TokenAllowance, List<TokenAllowance>> generateTokenAllowance(
            EntityId contractResultSenderId, Long ownerAccountId) {
        var builder = domainBuilder.tokenAllowance();
        if (contractResultSenderId != null) {
            builder.customize(t -> t.owner(ownerAccountId));
        }
        var currentTokenAllowance = builder.persist();
        var range = currentTokenAllowance.getTimestampRange();
        var rangeUpdate1 = Range.closedOpen(range.lowerEndpoint() - 2, range.lowerEndpoint() - 1);
        var rangeUpdate2 = Range.closedOpen(range.lowerEndpoint() - 1, range.lowerEndpoint());

        var update1 = builder.customize(t -> t.amount(999L).amountGranted(999L).timestampRange(rangeUpdate1))
                .get();
        var update2 = builder.customize(t -> t.amount(0).amountGranted(0L).timestampRange(rangeUpdate2))
                .get();
        saveHistory(update1);
        saveHistory(update2);

        if (contractResultSenderId != null) {
            var contractResult = domainBuilder.contractResult();
            for (var tokenAllowance : List.of(currentTokenAllowance, update1, update2)) {
                contractResult
                        .customize(c -> c.senderId(contractResultSenderId)
                                .consensusTimestamp(tokenAllowance.getTimestampLower()))
                        .persist();
            }
        }

        return Pair.of(currentTokenAllowance, List.of(update1, update2));
    }

    private void saveHistory(TokenAllowance tokenAllowance) {
        domainBuilder
                .tokenAllowanceHistory()
                .customize(c -> c.amount(tokenAllowance.getAmount())
                        .amountGranted(tokenAllowance.getAmountGranted())
                        .owner(tokenAllowance.getOwner())
                        .payerAccountId(tokenAllowance.getPayerAccountId())
                        .spender(tokenAllowance.getSpender())
                        .timestampRange(tokenAllowance.getTimestampRange())
                        .tokenId(tokenAllowance.getTokenId()))
                .persist();
    }
}
