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

import java.util.Collection;
import java.util.HashSet;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.EntityRepository;

/**
 * Loads entities to Postgres using COPY.
 */
@Named
@Log4j2
public class DomainWriterImpl implements DomainWriter {

    private final AccountBalanceRepository accountBalanceRepository;
    private final EntityRepository entityRepository;

    private final Collection<AccountBalance> accountBalances;
    private final Collection<Entity> entities;

    public DomainWriterImpl(AccountBalanceRepository accountBalanceRepository, EntityRepository entityRepository) {
        this.accountBalanceRepository = accountBalanceRepository;
        this.entityRepository = entityRepository;
        accountBalances = new HashSet<>();
        entities = new HashSet<>();
    }

    @Override
    public void flush() {
        log.info("Saving {} entities", entities.size());
        accountBalanceRepository.saveAll(accountBalances);
        entityRepository.saveAll(entities);
    }

    @Override
    public void onEntity(Entity entity) {
        entities.add(entity);
    }

    @Override
    public void onAccountBalance(AccountBalance accountBalance) {
        accountBalances.add(accountBalance);
    }
}
