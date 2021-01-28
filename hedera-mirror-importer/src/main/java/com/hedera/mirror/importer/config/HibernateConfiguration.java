package com.hedera.mirror.importer.config;

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

import java.util.Map;
import org.hibernate.EmptyInterceptor;
import org.hibernate.Interceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(prefix = "hedera.mirror.importer.db", name = "loadBalance", havingValue = "false")
@Configuration
public class HibernateConfiguration implements HibernatePropertiesCustomizer {
    @Override
    public void customize(final Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.session_factory.interceptor", hibernateInterceptor());
    }

    @Bean
    public Interceptor hibernateInterceptor() {
        // https://www.pgpool.net/docs/latest/en/html/runtime-config-load-balancing.html
        // pgpool disables load balancing for SQL statements beginning with an arbitrary comment and sends them to the
        // master / primary node. This is used to prevent the stale read-after-write issue.
        return new EmptyInterceptor() {
            @Override
            public String onPrepareStatement(final String sql) {
                return "/* NO PGPOOL LOAD BALANCE */\n" + sql;
            }
        };
    }
}
