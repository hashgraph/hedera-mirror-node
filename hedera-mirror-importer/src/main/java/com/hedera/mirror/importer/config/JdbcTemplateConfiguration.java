package com.hedera.mirror.importer.config;

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

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.JdbcProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.db.DBProperties;

@Configuration
class JdbcTemplateConfiguration {

    @Bean
    @Primary
    JdbcTemplate jdbcTemplate(DataSource dataSource, JdbcProperties properties) {
        return createJdbcTemplate(dataSource, properties);
    }

    @Bean
    @Owner
    JdbcTemplate jdbcTemplateOwner(DBProperties dbProperties, JdbcProperties properties) {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s?tcpKeepAlive=true", dbProperties.getHost(),
                dbProperties.getPort(), dbProperties.getName());
        var datasource = DataSourceBuilder.create()
                .password(dbProperties.getOwnerPassword())
                .url(jdbcUrl)
                .username(dbProperties.getOwner())
                .build();
        return createJdbcTemplate(datasource, properties);
    }

    private JdbcTemplate createJdbcTemplate(DataSource dataSource, JdbcProperties properties) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcProperties.Template template = properties.getTemplate();
        jdbcTemplate.setFetchSize(template.getFetchSize());
        jdbcTemplate.setMaxRows(template.getMaxRows());
        if (template.getQueryTimeout() != null) {
            jdbcTemplate.setQueryTimeout((int) template.getQueryTimeout().getSeconds());
        }
        return jdbcTemplate;
    }
}
