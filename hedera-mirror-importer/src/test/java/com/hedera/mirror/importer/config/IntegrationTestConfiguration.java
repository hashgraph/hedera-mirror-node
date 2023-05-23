/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.config;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.hedera.mirror.common.domain.DomainBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.util.stream.Collectors;
import kotlin.text.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.ResourceUtils;

@TestConfiguration
public class IntegrationTestConfiguration {

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    DomainBuilder domainBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
        return new DomainBuilder(entityManager, transactionOperations);
    }

    @Bean("cleanupSql")
    @Profile("!v2")
    String getV1CleanupSql() throws IOException {
        var file = ResourceUtils.getFile("classpath:db/scripts/cleanup.sql");
        return FileUtils.readFileToString(file, Charsets.UTF_8);
    }

    @Bean("cleanupSql")
    @Profile("v2")
    String getV2CleanupSql(JdbcOperations jdbcOperations) {
        var tables = jdbcOperations.query(
                """
                        select table_name
                        from information_schema.tables
                        left join time_partitions on partition::text = table_name::text
                        where table_schema = 'public' and table_type <> 'VIEW'
                          and table_name !~ '.*(flyway|transaction_type|citus_|_\\d+).*' and partition is null
                        order by table_name
                        """,
                (rs, rowNum) -> rs.getString(1));
        return tables.stream().map((t) -> String.format("delete from %s;", t)).collect(Collectors.joining("\n"));
    }

    @Bean
    RetryRecorder retryRecorder() {
        return new RetryRecorder();
    }

    // Records retry attempts made via Spring @Retryable or RetryTemplate for verification in tests
    public class RetryRecorder implements RetryListener {

        private final Multiset<Class<? extends Throwable>> retries = ConcurrentHashMultiset.create();

        @Override
        public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable t) {
            retries.add(t.getClass());
        }

        public int getRetries(Class<? extends Throwable> throwableClass) {
            return retries.count(throwableClass);
        }

        public void reset() {
            retries.clear();
        }
    }
}
