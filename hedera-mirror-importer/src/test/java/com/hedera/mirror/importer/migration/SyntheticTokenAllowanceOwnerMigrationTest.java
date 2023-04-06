package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlEntityListener;
import com.hedera.mirror.importer.repository.TokenAllowanceHistoryRepository;
import com.hedera.mirror.importer.repository.TokenAllowanceRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class SyntheticTokenAllowanceOwnerMigrationTest extends IntegrationTest {

    private final @Owner JdbcTemplate jdbcTemplate;
    private final SqlEntityListener sqlEntityListener;
    private final SyntheticTokenAllowanceOwnerMigration migration;
    private final TokenAllowanceHistoryRepository tokenAllowanceHistoryRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final TransactionTemplate transactionTemplate;

    private final String idColumns = "payer_account_id, spender, token_id";

    @AfterEach
    void teardown() {
        jdbcTemplate.execute("truncate token_allowance, token_allowance_history, contract_result;");
    }

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isEqualTo(1);
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(tokenAllowanceRepository.findAll()).isEmpty();
        assertThat(tokenAllowanceHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var contractResultSenderId = EntityId.of("0.0.2001", CONTRACT);
        long incorrectOwnerAccountId = 1001L;
        // A token allowance and historical token allowances that all have corresponding Contract Results.
        // The token allowances are saved with an incorrect owner
        var affectedTokenAllowancePair = generateTokenAllowance(contractResultSenderId, incorrectOwnerAccountId);

        // A token allowance can occur through HAPI and absent a corresponding Contract Result.
        // This token allowance and it's history should be unaffected by the migration
        var unaffectedTokenAllowancePair = generateTokenAllowance(null, null);

        // Before migration verify that the token allowances have the incorrect owner
        assertThat(tokenAllowanceRepository.findAll()).filteredOn(t -> t.getOwner() == incorrectOwnerAccountId)
                .containsExactlyInAnyOrderElementsOf(affectedTokenAllowancePair.getLeft());
        assertThat(findHistory(TokenAllowance.class, idColumns)).filteredOn(t -> t.getOwner() == incorrectOwnerAccountId)
                .containsExactlyInAnyOrderElementsOf(affectedTokenAllowancePair.getRight());

        // when
        migration.doMigrate();

        // then
        var expected = new ArrayList<>(affectedTokenAllowancePair.getLeft());
        // The owner should be set to the contract result sender id
        expected.forEach(t -> t.setOwner(contractResultSenderId.getId()));
        expected.addAll(unaffectedTokenAllowancePair.getLeft());
        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        // The history of the token allowance should also be updated with the corrected owner
        var expectedHistory = new ArrayList<>(affectedTokenAllowancePair.getRight());
        // The owner should be set to the contract result sender id
        expectedHistory.forEach(t -> t.setOwner(contractResultSenderId.getId()));
        expectedHistory.addAll(unaffectedTokenAllowancePair.getRight());
        assertThat(findHistory(TokenAllowance.class, idColumns)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    @Test
    void migrateWithCorrectOwner() {
        // given
        // A token allowance with history that has corresponding Contract Results.
        var contractResultSenderId = EntityId.of("0.0.2001", CONTRACT);
        // The token allowances already have the correct owner
        var tokenAllowancePair = generateTokenAllowance(contractResultSenderId, contractResultSenderId.getId());

        // when
        migration.doMigrate();

        // then
        // The token allowances should be unaffected by the migration
        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenAllowancePair.getLeft());
        assertThat(findHistory(TokenAllowance.class, idColumns)).containsExactlyInAnyOrderElementsOf(tokenAllowancePair.getRight());
    }

    /**
     * Creates a token allowance and updates it twice to populate token_allowance and token_allowance_history
     *
     * @param contractResultSenderId
     * @param ownerAccountId
     * @return the current token allowance and the historical token allowances
     */
    private Pair<List<TokenAllowance>, List<TokenAllowance>> generateTokenAllowance(EntityId contractResultSenderId,
                                                                                    Long ownerAccountId) {
        var builder = domainBuilder.tokenAllowance();
        if (contractResultSenderId != null) {
            builder = builder.customize(t -> t.owner(ownerAccountId));
        }

        var createTokenAllowance = builder.get();
        var tokenAllowanceUpdate1 = builder.customize(c -> c.amount(999L)).get();
        tokenAllowanceUpdate1.setTimestampLower(createTokenAllowance.getTimestampLower() + 1);
        var tokenAllowanceUpdate2 = builder.customize(c -> c.amount(55L)).get();
        tokenAllowanceUpdate2.setTimestampLower(createTokenAllowance.getTimestampLower() + 2);

        // Save the token allowance and its updates to populate token_allowance and token_allowance_history
        saveTokenAllowance(createTokenAllowance, contractResultSenderId);
        saveTokenAllowance(tokenAllowanceUpdate1, contractResultSenderId);
        saveTokenAllowance(tokenAllowanceUpdate2, contractResultSenderId);

        var expectedCreate = TestUtils.clone(createTokenAllowance);
        var expectedUpdate1 = TestUtils.merge(createTokenAllowance, tokenAllowanceUpdate1);
        var expectedUpdate2 = TestUtils.merge(expectedUpdate1, tokenAllowanceUpdate2);
        expectedCreate.setTimestampUpper(tokenAllowanceUpdate1.getTimestampLower());
        expectedUpdate1.setTimestampUpper(tokenAllowanceUpdate2.getTimestampLower());

        var tokenAllowanceExpected = List.of(expectedUpdate2);
        var tokenAllowanceHistoryExpected = List.of(expectedCreate, expectedUpdate1);
        return Pair.of(tokenAllowanceExpected, tokenAllowanceHistoryExpected);
    }

    private void saveTokenAllowance(TokenAllowance tokenAllowance, EntityId contractResultSenderId) {
        sqlEntityListener.onTokenAllowance(tokenAllowance);
        transactionTemplate.executeWithoutResult(status -> sqlEntityListener.onEnd(null));
        if (contractResultSenderId != null) {
            domainBuilder.contractResult()
                    .customize(c -> c.senderId(contractResultSenderId)
                            .consensusTimestamp(tokenAllowance.getTimestampLower()))
                    .persist();
        }
    }
}
