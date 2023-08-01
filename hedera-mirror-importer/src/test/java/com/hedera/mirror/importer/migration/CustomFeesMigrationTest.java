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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.converter.ObjectToStringSerializer;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.FixedFee;
import com.hedera.mirror.common.domain.transaction.FractionalFee;
import com.hedera.mirror.common.domain.transaction.RoyaltyFee;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.migration.CustomFeesMigrationTest.MigrationCustomFee.Id;
import com.hedera.mirror.importer.repository.CustomFeeRepository;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = {"spring.flyway.target=1.83.2"})
class CustomFeesMigrationTest extends IntegrationTest {

    private final CustomFeeRepository customFeeRepository;
    private final @Owner JdbcTemplate jdbcTemplate;

    @Value("classpath:db/migration/v1/V1.83.2__custom_fee_aggregate_history.sql")
    private final Resource sql;

    private static final String REVERT_DDL =
            """
            drop table if exists custom_fee;
            drop table if exists custom_fee_history;
            create table custom_fee (
                all_collectors_are_exempt boolean not null default false,
                amount                    bigint,
                amount_denominator        bigint,
                collector_account_id      bigint,
                created_timestamp         bigint  not null,
                denominating_token_id     bigint,
                maximum_amount            bigint,
                minimum_amount            bigint  not null default 0,
                net_of_transfers          boolean,
                royalty_denominator       bigint,
                royalty_numerator         bigint,
                token_id                  bigint  not null
            );
            create index if not exists custom_fee__token
               on custom_fee (token_id desc, created_timestamp desc);
            """;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute(REVERT_DDL);
        assertThat(ObjectToStringSerializer.INSTANCE).isNotNull();
    }

    @Test
    void empty() {
        runMigration();
        assertThat(customFeeRepository.findAll()).isEmpty();
        assertThat(getHistory()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var fixedFees = new MigrationCustomFeeData(getFixedFee());
        var fractionalFees = new MigrationCustomFeeData(getFractionalFee());
        var royaltyFees = new MigrationCustomFeeData(getRoyaltyFee());
        var emptyFees = new MigrationCustomFeeData(getEmptyFee());

        var migrationCustomFees = new ArrayList<MigrationCustomFee>();
        migrationCustomFees.addAll(fixedFees.migrationCustomFees);
        migrationCustomFees.addAll(fractionalFees.migrationCustomFees);
        migrationCustomFees.addAll(royaltyFees.migrationCustomFees);
        migrationCustomFees.addAll(emptyFees.migrationCustomFees);
        persistMigrationCustomFees(migrationCustomFees);

        // when
        runMigration();

        // then
        var expected = List.of(
                fixedFees.aggregateFee, royaltyFees.aggregateFee, fractionalFees.aggregateFee, emptyFees.aggregateFee);
        assertThat(customFeeRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        var history = getHistory();
        var expectedHistory = new ArrayList<CustomFee>();
        expectedHistory.addAll(fixedFees.aggregateHistory);
        expectedHistory.addAll(royaltyFees.aggregateHistory);
        expectedHistory.addAll(fractionalFees.aggregateHistory);
        expectedHistory.addAll(emptyFees.aggregateHistory);
        assertThat(history).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    @Test
    void migrateAggregateToSingleCustomFee() {
        // given
        var fixedFee = getFixedFee();
        var fixedFee2 = getFixedFee();
        var fractionalFee = getFractionalFee();
        var fractionalFee2 = getFractionalFee();
        var royaltyFee = getRoyaltyFee();
        var royaltyFee2 = getRoyaltyFee();

        // Set all token ids and createdTimestamps to the same value, they will then be aggregated into a single custom
        // fee
        fixedFee2.id.tokenId = fixedFee.id.tokenId;
        fixedFee2.id.createdTimestamp = fixedFee.id.createdTimestamp;
        fractionalFee.id.tokenId = fixedFee.id.tokenId;
        fractionalFee.id.createdTimestamp = fixedFee.id.createdTimestamp;
        fractionalFee2.id.tokenId = fixedFee.id.tokenId;
        fractionalFee2.id.createdTimestamp = fixedFee.id.createdTimestamp;
        royaltyFee.id.tokenId = fixedFee.id.tokenId;
        royaltyFee.id.createdTimestamp = fixedFee.id.createdTimestamp;
        royaltyFee2.id.tokenId = fixedFee.id.tokenId;
        royaltyFee2.id.createdTimestamp = fixedFee.id.createdTimestamp;

        var fixedFees = new MigrationCustomFeeData(fixedFee);
        var fixedFees2 = new MigrationCustomFeeData(fixedFee2);
        var fractionalFees = new MigrationCustomFeeData(fractionalFee);
        var fractionalFees2 = new MigrationCustomFeeData(fractionalFee2);
        var royaltyFees = new MigrationCustomFeeData(royaltyFee);
        var royaltyFees2 = new MigrationCustomFeeData(royaltyFee2);

        var migrationCustomFees = new ArrayList<MigrationCustomFee>();
        migrationCustomFees.addAll(fixedFees.migrationCustomFees);
        migrationCustomFees.addAll(fixedFees2.migrationCustomFees);
        migrationCustomFees.addAll(fractionalFees.migrationCustomFees);
        migrationCustomFees.addAll(fractionalFees2.migrationCustomFees);
        migrationCustomFees.addAll(royaltyFees.migrationCustomFees);
        migrationCustomFees.addAll(royaltyFees2.migrationCustomFees);
        persistMigrationCustomFees(migrationCustomFees);

        // when
        runMigration();

        // then
        var aggregates = List.of(
                fixedFees.aggregateFee,
                fixedFees2.aggregateFee,
                fractionalFees.aggregateFee,
                fractionalFees2.aggregateFee,
                royaltyFees.aggregateFee,
                royaltyFees2.aggregateFee);
        var expected = combineAggregates(aggregates, false);
        var repositoryResult = customFeeRepository.findAll();
        var listAssert = Assertions.assertThat(repositoryResult).hasSize(1);
        for (var result : expected) {
            listAssert.anySatisfy(fee -> {
                assertThat(fee.getFixedFees()).containsExactlyInAnyOrderElementsOf(result.getFixedFees());
                assertThat(fee.getFractionalFees()).containsExactlyInAnyOrderElementsOf(result.getFractionalFees());
                assertThat(fee.getRoyaltyFees()).containsExactlyInAnyOrderElementsOf(result.getRoyaltyFees());
                assertThat(fee.getCreatedTimestamp()).isEqualTo(result.getCreatedTimestamp());
                assertThat(fee.getTokenId()).isEqualTo(result.getTokenId());
                assertThat(fee.getTimestampRange()).isEqualTo(result.getTimestampRange());
            });
        }

        var aggregateHistory = new ArrayList<CustomFee>();
        aggregateHistory.addAll(fixedFees.aggregateHistory);
        aggregateHistory.addAll(fixedFees2.aggregateHistory);
        aggregateHistory.addAll(fractionalFees.aggregateHistory);
        aggregateHistory.addAll(fractionalFees2.aggregateHistory);
        aggregateHistory.addAll(royaltyFees.aggregateHistory);
        aggregateHistory.addAll(royaltyFees2.aggregateHistory);
        var expectedHistory = combineAggregates(aggregateHistory, true);
        var history = getHistory();
        var historyListAssert = Assertions.assertThat(history).hasSize(2);
        for (var historyResult : expectedHistory) {
            historyListAssert.anySatisfy(h -> {
                assertThat(h.getFixedFees()).containsExactlyInAnyOrderElementsOf(historyResult.getFixedFees());
                assertThat(h.getFractionalFees())
                        .containsExactlyInAnyOrderElementsOf(historyResult.getFractionalFees());
                assertThat(h.getRoyaltyFees()).containsExactlyInAnyOrderElementsOf(historyResult.getRoyaltyFees());
                assertThat(h.getCreatedTimestamp()).isEqualTo(historyResult.getCreatedTimestamp());
                assertThat(h.getTokenId()).isEqualTo(historyResult.getTokenId());
                assertThat(h.getTimestampRange()).isEqualTo(historyResult.getTimestampRange());
            });
        }
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = sql.getInputStream()) {
            jdbcTemplate.update(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
        }
    }

    private MigrationCustomFee.MigrationCustomFeeBuilder getEmptyFee() {
        var id = new MigrationCustomFee.Id(domainBuilder.timestamp(), domainBuilder.id());
        return MigrationCustomFee.builder().id(id).allCollectorsAreExempt(false).minimumAmount(0L);
    }

    private MigrationCustomFee.MigrationCustomFeeBuilder getFixedFee() {
        var id = new MigrationCustomFee.Id(domainBuilder.timestamp(), domainBuilder.id());
        return MigrationCustomFee.builder()
                .allCollectorsAreExempt(false)
                .amount(domainBuilder.id())
                .denominatingTokenId(domainBuilder.id())
                .id(id)
                .collectorAccountId(domainBuilder.id());
    }

    private MigrationCustomFee.MigrationCustomFeeBuilder getFractionalFee() {
        var id = new MigrationCustomFee.Id(domainBuilder.timestamp(), domainBuilder.id());
        return MigrationCustomFee.builder()
                .allCollectorsAreExempt(false)
                .amount(domainBuilder.id())
                .denominatingTokenId(domainBuilder.id())
                .amountDenominator(domainBuilder.id())
                .id(id)
                .collectorAccountId(domainBuilder.id())
                .maximumAmount(domainBuilder.id())
                .minimumAmount(1L)
                .netOfTransfers(true);
    }

    private MigrationCustomFee.MigrationCustomFeeBuilder getRoyaltyFee() {
        var id = new MigrationCustomFee.Id(domainBuilder.timestamp(), domainBuilder.id());
        return MigrationCustomFee.builder()
                .allCollectorsAreExempt(false)
                .amount(domainBuilder.id())
                .id(id)
                .collectorAccountId(domainBuilder.id())
                .denominatingTokenId(domainBuilder.id())
                .royaltyDenominator(10L)
                .royaltyNumerator(1L);
    }

    private class MigrationCustomFeeData {
        public final List<MigrationCustomFee> migrationCustomFees;
        public final CustomFee aggregateFee;
        public final List<CustomFee> aggregateHistory;

        public MigrationCustomFeeData(MigrationCustomFee.MigrationCustomFeeBuilder builder) {
            var range = Range.atLeast(builder.id.createdTimestamp);
            var createdTimestamp = range.lowerEndpoint() - 2;
            var rangeUpdate1 = Range.closedOpen(createdTimestamp, range.lowerEndpoint() - 1);
            var rangeUpdate2 = Range.closedOpen(range.lowerEndpoint() - 1, range.lowerEndpoint());
            var currentMigrationCustomFee = builder.build();
            var updateCollectorAccountId1 = domainBuilder.id();
            var updateCollectorAccountId2 = domainBuilder.id();
            var migrationCustomFeeUpdate1 = builder.id(new Id(rangeUpdate1.lowerEndpoint(), builder.id.tokenId))
                    .collectorAccountId(updateCollectorAccountId1)
                    .build();
            var migrationCustomFeeUpdate2 = builder.id(
                            new MigrationCustomFee.Id(rangeUpdate2.lowerEndpoint(), builder.id.tokenId))
                    .collectorAccountId(updateCollectorAccountId2)
                    .build();

            var currentTimestamp = currentMigrationCustomFee.id.createdTimestamp;
            migrationCustomFees =
                    List.of(currentMigrationCustomFee, migrationCustomFeeUpdate1, migrationCustomFeeUpdate2);

            // build aggregate fees
            var updatedCurrent = builder.id(new Id(rangeUpdate2.upperEndpoint(), builder.id.tokenId))
                    .collectorAccountId(currentMigrationCustomFee.collectorAccountId)
                    .build();
            var updatedHistory = builder.id(new Id(createdTimestamp, builder.id.tokenId))
                    .collectorAccountId(updateCollectorAccountId1)
                    .build();
            var updatedHistory2 = builder.id(new Id(rangeUpdate1.upperEndpoint(), builder.id.tokenId))
                    .collectorAccountId(updateCollectorAccountId2)
                    .build();
            aggregateFee = toAggregateCustomFees(List.of(updatedCurrent)).get(0);
            aggregateFee.setTimestampRange(Range.atLeast(currentTimestamp));

            var aggregateHistory1 = toAggregateCustomFees(List.of(updatedHistory));
            aggregateHistory1.get(0).setTimestampRange(rangeUpdate1);
            var aggregateHistory2 = toAggregateCustomFees(List.of(updatedHistory2));
            aggregateHistory2.get(0).setTimestampRange(rangeUpdate2);

            aggregateHistory = List.of(aggregateHistory1.get(0), aggregateHistory2.get(0));
        }
    }

    private List<CustomFee> toAggregateCustomFees(List<MigrationCustomFee> migrationCustomFees) {
        Map<Long, CustomFee> customFeeMap = new HashMap<>();
        for (MigrationCustomFee migrationCustomFee : migrationCustomFees) {
            var mappedFee = customFeeMap.get(migrationCustomFee.id.tokenId);
            var customFee = mappedFee != null
                    ? mappedFee
                    : domainBuilder
                            .customFee()
                            .customize(c -> c.createdTimestamp(migrationCustomFee.id.createdTimestamp)
                                    .tokenId(migrationCustomFee.id.tokenId)
                                    .fixedFees(null)
                                    .fractionalFees(null)
                                    .royaltyFees(null))
                            .get();

            if (migrationCustomFee.denominatingTokenId != null && migrationCustomFee.royaltyDenominator == null) {
                customFee.addFixedFee(FixedFee.builder()
                        .allCollectorsAreExempt(migrationCustomFee.allCollectorsAreExempt)
                        .amount(migrationCustomFee.amount)
                        .collectorAccountId(EntityId.of(migrationCustomFee.collectorAccountId, ACCOUNT))
                        .denominatingTokenId(EntityId.of(migrationCustomFee.denominatingTokenId, TOKEN))
                        .build());
            }
            if (migrationCustomFee.amountDenominator != null) {
                customFee.addFractionalFee(FractionalFee.builder()
                        .amount(migrationCustomFee.amount)
                        .amountDenominator(migrationCustomFee.amountDenominator)
                        .maximumAmount(migrationCustomFee.maximumAmount)
                        .minimumAmount(migrationCustomFee.minimumAmount)
                        .netOfTransfers(migrationCustomFee.netOfTransfers)
                        .collectorAccountId(EntityId.of(migrationCustomFee.collectorAccountId, ACCOUNT))
                        .allCollectorsAreExempt(migrationCustomFee.allCollectorsAreExempt)
                        .build());
            }
            if (migrationCustomFee.royaltyDenominator != null) {
                var royaltyFee = RoyaltyFee.builder()
                        .royaltyDenominator(migrationCustomFee.royaltyDenominator)
                        .royaltyNumerator(migrationCustomFee.royaltyNumerator)
                        .collectorAccountId(EntityId.of(migrationCustomFee.collectorAccountId, ACCOUNT))
                        .allCollectorsAreExempt(migrationCustomFee.allCollectorsAreExempt);
                if (migrationCustomFee.getAmount() != null) {
                    royaltyFee.fallbackFee(FixedFee.builder()
                            .amount(migrationCustomFee.amount)
                            .denominatingTokenId(EntityId.of(migrationCustomFee.denominatingTokenId, TOKEN))
                            .collectorAccountId(EntityId.of(migrationCustomFee.collectorAccountId, ACCOUNT))
                            .allCollectorsAreExempt(migrationCustomFee.allCollectorsAreExempt)
                            .build());
                }
                customFee.addRoyaltyFee(royaltyFee.build());
            }

            customFeeMap.putIfAbsent(migrationCustomFee.id.tokenId, customFee);
        }

        return customFeeMap.values().stream().toList();
    }

    private List<CustomFee> combineAggregates(List<CustomFee> customFees, boolean history) {
        Map<CustomFeeMapKey, CustomFee> customFeeMap = new HashMap<>();
        for (var customFee : customFees) {
            var keyRange = history ? customFee.getTimestampRange() : null;
            var key = new CustomFeeMapKey(customFee.getTokenId(), keyRange);
            if (!customFeeMap.containsKey(key)) {
                customFeeMap.put(key, customFee);
            } else {
                var mappedFee = customFeeMap.get(key);
                if (customFee.getFixedFees() != null) {
                    for (var fixedFee : customFee.getFixedFees()) {
                        mappedFee.addFixedFee(fixedFee);
                    }
                }
                if (customFee.getFractionalFees() != null) {
                    for (var fractionalFee : customFee.getFractionalFees()) {
                        mappedFee.addFractionalFee(fractionalFee);
                    }
                }
                if (customFee.getRoyaltyFees() != null) {
                    for (var royaltyFee : customFee.getRoyaltyFees()) {
                        mappedFee.addRoyaltyFee(royaltyFee);
                    }
                }
            }
        }
        return customFeeMap.values().stream().toList();
    }

    private void persistMigrationCustomFees(List<MigrationCustomFee> migrationCustomFees) {
        jdbcTemplate.batchUpdate(
                """
                        insert into custom_fee (created_timestamp, token_id, all_collectors_are_exempt, amount, amount_denominator, collector_account_id, denominating_token_id, maximum_amount, minimum_amount, net_of_transfers, royalty_denominator, royalty_numerator)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                migrationCustomFees.stream()
                        .map(customFee -> new Object[] {
                            customFee.id.createdTimestamp,
                            customFee.id.tokenId,
                            customFee.allCollectorsAreExempt,
                            customFee.amount,
                            customFee.amountDenominator,
                            customFee.collectorAccountId,
                            customFee.denominatingTokenId,
                            customFee.maximumAmount,
                            customFee.minimumAmount,
                            customFee.netOfTransfers,
                            customFee.royaltyDenominator,
                            customFee.royaltyNumerator
                        })
                        .toList());
    }

    private List<CustomFee> getHistory() {
        String sql = "select * from custom_fee_history order by token_id, timestamp_range asc";
        List<CustomFee> customFees = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            long createdTimestamp = rs.getLong(1);
            long token_id = rs.getLong(2);
            List<FixedFee> fixedFees = convertFixedFees(rs.getObject(3, PGobject.class));
            List<FractionalFee> fractionalFees = convertFractionalFees(rs.getObject(4, PGobject.class));
            List<RoyaltyFee> royaltyFees = convertRoyaltyFees(rs.getObject(5, PGobject.class));
            var timestampRange = convertRange(rs.getObject(6, PGobject.class));
            customFees.add(CustomFee.builder()
                    .createdTimestamp(createdTimestamp)
                    .tokenId(token_id)
                    .fixedFees(fixedFees)
                    .fractionalFees(fractionalFees)
                    .royaltyFees(royaltyFees)
                    .timestampRange(timestampRange)
                    .build());
        });

        return customFees;
    }

    @SneakyThrows
    private List<FixedFee> convertFixedFees(PGobject pgobject) {
        if (pgobject.isNull()) {
            return null;
        }
        var fixedFees = new ArrayList<FixedFee>();
        var jsonArray = new JSONArray(pgobject.getValue());
        for (int i = 0; i < jsonArray.length(); i++) {
            var item = jsonArray.getJSONObject(i);
            fixedFees.add(FixedFee.builder()
                    .allCollectorsAreExempt(item.getBoolean("all_collectors_are_exempt"))
                    .amount(item.getLong("amount"))
                    .collectorAccountId(EntityId.of(item.getLong("collector_account_id"), ACCOUNT))
                    .denominatingTokenId(EntityId.of(item.getLong("denominating_token_id"), TOKEN))
                    .build());
        }
        return fixedFees;
    }

    @SneakyThrows
    private List<FractionalFee> convertFractionalFees(PGobject pgobject) {
        if (pgobject.isNull()) {
            return null;
        }
        var fractionalFees = new ArrayList<FractionalFee>();
        var jsonArray = new JSONArray(pgobject.getValue());
        for (int i = 0; i < jsonArray.length(); i++) {
            var item = jsonArray.getJSONObject(i);
            fractionalFees.add(FractionalFee.builder()
                    .allCollectorsAreExempt(item.getBoolean("all_collectors_are_exempt"))
                    .amount(item.getLong("amount"))
                    .amountDenominator(item.getLong("amount_denominator"))
                    .netOfTransfers(item.getBoolean("net_of_transfers"))
                    .maximumAmount(item.getLong("maximum_amount"))
                    .minimumAmount(item.getLong("minimum_amount"))
                    .collectorAccountId(EntityId.of(item.getLong("collector_account_id"), ACCOUNT))
                    .build());
        }
        return fractionalFees;
    }

    @SneakyThrows
    private List<RoyaltyFee> convertRoyaltyFees(PGobject pgobject) {
        if (pgobject.isNull()) {
            return null;
        }
        var royaltyFees = new ArrayList<RoyaltyFee>();
        var jsonArray = new JSONArray(pgobject.getValue());
        for (int i = 0; i < jsonArray.length(); i++) {
            var item = jsonArray.getJSONObject(i);
            var builder = RoyaltyFee.builder()
                    .allCollectorsAreExempt(item.getBoolean("all_collectors_are_exempt"))
                    .collectorAccountId(EntityId.of(item.getLong("collector_account_id"), ACCOUNT))
                    .royaltyDenominator(item.getLong("royalty_denominator"))
                    .royaltyNumerator(item.getLong("royalty_numerator"));

            if (item.has("fallback_fee")) {
                var fallBackFee = item.getJSONObject("fallback_fee");
                builder.fallbackFee(FixedFee.builder()
                        .allCollectorsAreExempt(fallBackFee.getBoolean("all_collectors_are_exempt"))
                        .amount(fallBackFee.getLong("amount"))
                        .collectorAccountId(EntityId.of(fallBackFee.getLong("collector_account_id"), ACCOUNT))
                        .denominatingTokenId(EntityId.of(fallBackFee.getLong("denominating_token_id"), TOKEN))
                        .build());
            }
            royaltyFees.add(builder.build());
        }
        return royaltyFees;
    }

    private Range<Long> convertRange(PGobject pgobject) {
        return PostgreSQLGuavaRangeType.longRange(pgobject.getValue());
    }

    @Data
    private class CustomFeeMapKey {
        public final long tokenId;
        public final Range<Long> timestampRange;
    }

    @AllArgsConstructor
    @Builder
    @Data
    static class MigrationCustomFee {
        private Id id;
        private boolean allCollectorsAreExempt;
        private Long amount;
        private Long amountDenominator;
        private Long collectorAccountId;
        private Long denominatingTokenId;
        private Long maximumAmount;
        private long minimumAmount;
        private Boolean netOfTransfers;
        private Long royaltyDenominator;
        private Long royaltyNumerator;

        @Data
        @AllArgsConstructor
        public static class Id {
            private long createdTimestamp;
            private long tokenId;
        }
    }
}
