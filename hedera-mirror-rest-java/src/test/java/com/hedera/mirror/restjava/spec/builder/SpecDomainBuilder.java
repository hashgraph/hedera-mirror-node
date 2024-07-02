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

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.DomainBuilder;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Used to build domain objects for use in spec testing. This is different from general unit tests utilizing
 * {@link DomainBuilder}, in that default values and overrides are based on the original REST module
 * integrationDomainOps.js and attribute names provided in the spec test JSON setup.
 */
@Component
@CustomLog
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SpecDomainBuilder {

    private final AccountBuilder accountBuilder;
    private final TokenAccountBuilder tokenAccountBuilder;
    private final TransactionBuilder transactionBuilder;

    public void addAccounts(List<Map<String, Object>> accounts) {
        accounts.forEach(accountBuilder::customizeAndPersistEntity);
    }

    public void addTokenAccounts(List<Map<String, Object>> tokenAccounts) {
        tokenAccounts.forEach(tokenAccountBuilder::customizeAndPersistEntity);
    }

    public void addTransactions(List<Map<String, Object>> transactions) {
        transactions.forEach(transactionBuilder::customizeAndPersistEntity);
    }
}
