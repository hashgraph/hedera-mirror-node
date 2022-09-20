package com.hedera.mirror.importer.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.db.DBProperties;

@Configuration
public class JdbcTemplateConfiguration {

    public static final String JDBC_TEMPLATE_OWNER = "owner";

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(JDBC_TEMPLATE_OWNER)
    public JdbcTemplate jdbcTemplate(DBProperties dbProperties) {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s?tcpKeepAlive=true", dbProperties.getHost(),
                dbProperties.getPort(), dbProperties.getName());
        var datasource = DataSourceBuilder.create()
                .password(dbProperties.getOwnerPassword())
                .url(jdbcUrl)
                .username(dbProperties.getOwner())
                .build();
        return new JdbcTemplate(datasource);
    }
}
