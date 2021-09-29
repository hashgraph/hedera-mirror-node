package com.hedera.mirror.importer.parser.balance.nft;

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

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.File;
import java.util.Optional;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;

import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.repository.NftBalanceRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.util.ShutdownHelper;

/**
 * This class builds nft balance snapshot by applying the changes from the nft transfers to the last snapshot.
 * The number of new nft transfers and the elapsed time from the last snapshot to the last nft transfer must both meet
 * the configured threshold to build a new nft balance snapshot.
 */
@Log4j2
@Named
public class NftBalanceSnapshotBuilder {

    private final Timer buildDurationMetricFailure;
    private final Timer buildDurationMetricSuccess;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final NftBalanceRepository nftBalanceRepository;
    private final NftTransferRepository nftTransferRepository;

    private final NftBalanceSnapshotProperties properties;

    @Value("classpath:db/scripts/build_nft_balance_snapshot.sql")
    private File sqlScript;

    public NftBalanceSnapshotBuilder(MeterRegistry meterRegistry, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                     NftBalanceRepository nftBalanceRepository,
                                     NftTransferRepository nftTransferRepository,
                                     NftBalanceSnapshotProperties properties) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.nftBalanceRepository = nftBalanceRepository;
        this.nftTransferRepository = nftTransferRepository;
        this.properties = properties;

        Timer.Builder buildDurationTimerBuilder = Timer.builder("hedera.mirror.nft.balance.duration")
                .description("The duration in seconds it took to build a nft snapshot");
        buildDurationMetricFailure = buildDurationTimerBuilder.tag("success", "false").register(meterRegistry);
        buildDurationMetricSuccess = buildDurationTimerBuilder.tag("success", "true").register(meterRegistry);
    }

    @Leader
    @Scheduled(fixedDelayString = "${hedera.mirror.importer.downloader.record.frequency:60000}")
    public void build() {
        if (!properties.isEnabled()) {
            return;
        }

        if (ShutdownHelper.isStopping()) {
            return;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean success = false;

        try {
            long lastSnapshotTimestamp = nftBalanceRepository.getLastTimestamp().orElse(0L);
            Optional<Long> endTimestampByAge = nftTransferRepository
                    .getTimestampAtOffsetAfter(lastSnapshotTimestamp + properties.getInterval().toNanos() - 1, 0);
            Optional<Long> endTimestampByTransferSize = nftTransferRepository
                    .getTimestampAtOffsetAfter(lastSnapshotTimestamp, properties.getTransferSize() - 1);
            if (endTimestampByAge.isEmpty() || endTimestampByTransferSize.isEmpty()) {
                log.info("Nft balance snapshot not old enough or minimum number of new nft transfers not reached, " +
                        "skip building snapshot");
                return;
            }

            long endTransferTimestamp = Math.max(endTimestampByAge.get(), endTimestampByTransferSize.get());
            SqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("last_snapshot_timestamp", lastSnapshotTimestamp)
                    .addValue("end_transfer_timestamp", endTransferTimestamp);
            namedParameterJdbcTemplate.update(FileUtils.readFileToString(sqlScript, Charsets.UTF_8), parameters);
            success = true;
            log.info("Successfully built nft balance snapshot at consensus timestamp {} in {}",
                    endTransferTimestamp, stopwatch);
        } catch (Exception ex){
            log.error("Failed to build nft balance snapshot after {}", stopwatch, ex);
        } finally {
            Timer timer = success ? buildDurationMetricSuccess : buildDurationMetricFailure;
            timer.record(stopwatch.elapsed());
        }
    }
}
