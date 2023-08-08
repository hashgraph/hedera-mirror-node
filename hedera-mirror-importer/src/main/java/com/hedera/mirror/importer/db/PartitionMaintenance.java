/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.db;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@CustomLog
@Component
@RequiredArgsConstructor
public class PartitionMaintenance {
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(initialDelay = 0, fixedRate = 1000000000000L)
    public void test() {
        String sql = "select create_time_partitions(table_name :='public.account_balance',\n"
                + "                              partition_interval := INTERVAL '1 month',\n"
                + "                              start_from := CURRENT_TIMESTAMP + '2 months',\n"
                + "                              end_at := CURRENT_TIMESTAMP + '24 months')";

        Boolean created = jdbcTemplate.queryForObject(sql, Boolean.class);
        log.info("I am a test {}", created);
    }
}
