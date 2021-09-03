package com.hedera.mirror.importer.repository.upsert;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.github.vertical_blank.sqlformatter.languages.Dialect;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.converter.NullableStringSerializer;
import com.hedera.mirror.importer.repository.AbstractRepositoryTest;

abstract class AbstractUpsertQueryGeneratorTest extends AbstractRepositoryTest {

    private static final SqlFormatter.Formatter SQL_FORMATTER = SqlFormatter.of(Dialect.PostgreSql);

    @Resource
    private DataSource dataSource;

    protected abstract UpsertQueryGenerator getUpdatableDomainRepositoryCustom();

    protected abstract String getInsertQuery();

    protected String getUpdateQuery() {
        return "";
    };

    @Test
    void insert() {
        String insertQuery = getUpdatableDomainRepositoryCustom().getInsertQuery()
                .replaceAll(NullableStringSerializer.NULLABLE_STRING_REPLACEMENT, "<uuid>");
        assertEqual(insertQuery, getInsertQuery());
    }

    @Test
    void insertContainsAllFields() {
        // verify all fields in a domain are captured to ensure we don't miss schema updates
        String insertQuery = getUpdatableDomainRepositoryCustom().getInsertQuery()
                .replaceAll(NullableStringSerializer.NULLABLE_STRING_REPLACEMENT, "<uuid>");

        // get tables from db
        List<String> columnsFromDb = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.getMetaData()
                     .getColumns(null, null, getUpdatableDomainRepositoryCustom().getFinalTableName(), null)) {

            while (rs.next()) {
                columnsFromDb.add(rs.getString("COLUMN_NAME"));
            }
        } catch (Exception e) {
            log.error("Unable to retrieve details from database", e);
        }

        // verify
        assertThat(insertQuery).contains(columnsFromDb);
    }

    @Test
    void update() {
        String updateQuery = getUpdatableDomainRepositoryCustom().getUpdateQuery()
                .replaceAll(NullableStringSerializer.NULLABLE_STRING_REPLACEMENT, "<uuid>");
        assertEqual(updateQuery, getUpdateQuery());
    }

    private void assertEqual(String actualSql, String expectedSql) {
        assertThat(formatSql(actualSql)).isEqualTo(formatSql(expectedSql));
    }

    private String formatSql(String sql) {
        if (StringUtils.isEmpty(sql)) {
            return "";
        }

        return SQL_FORMATTER.format(sql);
    }
}
