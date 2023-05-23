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

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(prefix = "hedera.mirror.importer.db", name = "loadBalance", havingValue = "false")
@Configuration
class HibernateConfiguration implements HibernatePropertiesCustomizer {

    private static final String NO_LOAD_BALANCE = "/* NO PGPOOL LOAD BALANCE */\n";

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.STATEMENT_INSPECTOR, statementInspector());
    }

    /**
     * https://www.pgpool.net/docs/latest/en/html/runtime-config-load-balancing.html
     * pgpool disables load balancing for SQL statements beginning with an arbitrary comment and sends them to the
     * primary node. This is used to prevent the stale read-after-write issue.
     */
    @Bean
    StatementInspector statementInspector() {
        return sql -> NO_LOAD_BALANCE + sql;
    }
}
