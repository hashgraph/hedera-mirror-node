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

package com.hedera.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.Owner;
import jakarta.inject.Named;
import java.io.IOException;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
class MergeDuplicateBlocksMigration extends RepeatableMigration {

    private static final String SQL =
            """
                    with block1 as (
                      delete from record_file
                      where consensus_end = 1675962000231859003 and index = 44029066
                      returning *
                    ),
                    merged_block as (
                      update record_file block2 set
                      consensus_start = block1.consensus_start,
                      count = block1.count + block2.count,
                      gas_used = block1.gas_used + block2.gas_used,
                      load_start = block1.load_start,
                      name = block1.name,
                      prev_hash = block1.prev_hash,
                      sidecar_count = block1.sidecar_count + block2.sidecar_count,
                      size = block1.size + block2.size
                      from block1
                      where block2.consensus_end = 1675962001984524003 and block1.index = block2.index
                      returning block2.*
                    )
                    update transaction t
                    set index = t.index + block1.count
                    from block1
                    where consensus_timestamp > 1675962000231859003 and consensus_timestamp <= 1675962001984524003;
                    """;

    private final JdbcTemplate jdbcTemplate;
    private final MirrorProperties mirrorProperties;

    @Lazy
    protected MergeDuplicateBlocksMigration(@Owner JdbcTemplate jdbcTemplate, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
        this.mirrorProperties = mirrorProperties;
    }

    @Override
    protected void doMigrate() throws IOException {
        if (!MirrorProperties.HederaNetwork.MAINNET.equalsIgnoreCase(mirrorProperties.getNetwork())) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        int count = jdbcTemplate.update(SQL);
        log.info("Successfully merged the blocks and fixed {} transaction indexes in {}", count, stopwatch);
    }

    @Override
    public String getDescription() {
        return "Fix duplicate block number issue on mainnet by merging them";
    }
}
