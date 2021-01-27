package com.hedera.datagenerator.domain.writer;
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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.domain.AccountBalance;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.record.entity.sql.PgCopy;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlProperties;
import com.hedera.mirror.importer.repository.EntityRepository;

/**
 * Loads entities to Postgres using COPY.
 */
@Named
@Log4j2
public class DomainWriterImpl implements DomainWriter {
    private final DataSource dataSource;

    private final EntityRepository entityRepository;
    private final PgCopy<AccountBalance> accountBalancePgCopy;
    private final SqlProperties sqlProperties;

    private final HashSet<AccountBalance> accountBalances;
    private final HashSet<Entities> entities;

    public DomainWriterImpl(DataSource dataSource, EntityRepository entityRepository, SqlProperties sqlProperties) throws SQLException {
        this.dataSource = dataSource;
        this.entityRepository = entityRepository;
        accountBalancePgCopy = new PgCopy<>(AccountBalance.class, new SimpleMeterRegistry(), sqlProperties);
        accountBalances = new HashSet<>();
        entities = new HashSet<>();
        this.sqlProperties = sqlProperties;
    }

    @Override
    public void flush() {
        try {
            accountBalancePgCopy.copy(accountBalances, dataSource.getConnection());
            log.info("Saving {} entities", entities.size());
            entityRepository.saveAll(entities);
        } catch (SQLException sqlex) {
            log.error(sqlex);
        }
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
