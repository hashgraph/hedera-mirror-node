package com.hedera.mirror.importer.repository.upsert;

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

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.importer.IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenAccountUpsertQueryGeneratorTest extends IntegrationTest {

    private final EntityMetadataRegistry entityMetadataRegistry;
    private final TokenAccountUpsertQueryGenerator upsertQueryGenerator;

    @Test
    void createTempIndexQuery() {
        var expected = "create index if not exists token_account_temp_idx on token_account_temp " +
                "(token_id, account_id)";
        var createTempIndexQuery = upsertQueryGenerator.getCreateTempIndexQuery();
        assertThat(createTempIndexQuery).isEqualTo(expected);
    }

    @Test
    void finalTableName() {
        var finalTableName = upsertQueryGenerator.getFinalTableName();
        assertThat(finalTableName).isEqualTo("token_account");
    }

    @Test
    void insertQuery() {
        var entityMetadata = entityMetadataRegistry.lookup(TokenAccount.class);
        var columns = entityMetadata.columns("{0}");
        var insertQuery = upsertQueryGenerator.getUpsertQuery();
        assertThat(insertQuery).isNotBlank().containsIgnoringWhitespaces(columns);
    }

    @Test
    void temporaryTableName() {
        var temporaryTableName = upsertQueryGenerator.getTemporaryTableName();
        assertThat(temporaryTableName).isEqualTo("token_account_temp");
    }
}
