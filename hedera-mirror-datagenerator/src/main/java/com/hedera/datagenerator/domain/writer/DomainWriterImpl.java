package com.hedera.datagenerator.domain.writer;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.domain.AccountBalance;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.record.entity.sql.PgCopy;

/**
 * Loads entities to Postgres using COPY.
 */
@Named
@Log4j2
public class DomainWriterImpl implements DomainWriter {
    private final DataSource dataSource;

    private final PgCopy<Entities> entitiesPgCopy;
    private final PgCopy<AccountBalance> accountBalancePgCopy;

    private List<AccountBalance> accountBalances;
    private List<Entities> entities;

    public DomainWriterImpl(DataSource dataSource) throws SQLException {
        this.dataSource = dataSource;
        accountBalancePgCopy = new PgCopy<>(getConnection(), AccountBalance.class);
        entitiesPgCopy = new PgCopy<>(getConnection(), Entities.class);
        accountBalances = new ArrayList<>();
        entities = new ArrayList<>();
    }

    @Override
    public void flush() {
        entitiesPgCopy.copy(entities);
        accountBalancePgCopy.copy(accountBalances);
    }

    @Override
    public void close() {
        entitiesPgCopy.close();
        accountBalancePgCopy.close();
    }

    @Override
    public void onEntity(Entities entity) {
        entities.add(entity);
    }

    @Override
    public void onAccountBalance(AccountBalance accountBalance) {
        accountBalances.add(accountBalance);
    }

    private Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            log.error("Error getting connection ", e);
            throw new ParserSQLException(e);
        }
    }
}
