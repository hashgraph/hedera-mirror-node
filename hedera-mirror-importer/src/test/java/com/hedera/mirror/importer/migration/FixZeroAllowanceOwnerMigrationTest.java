package com.hedera.mirror.importer.migration;

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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;

@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.56.0")
public class FixZeroAllowanceOwnerMigrationTest extends IntegrationTest  {

    private static final Converter<String, String> CASE_CONVERTER = CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE);

    private static final String TABLE_IDS = "owner";

    @Value("classpath:db/migration/v1/V1.56.1__fix_zero_allowance_owner.sql")
    private File migrationSql;

    private final AtomicLong id = new AtomicLong(10);

    private final AtomicLong timestamp = new AtomicLong(200);

    @Test
    void empty() {
        migrate();
        assertThat(findAllAllowances()).isEmpty();
    }

    @Test
    void fixZeroAllowanceOwner() {
        // given
        var expected = Stream.of(CryptoAllowance.class, NftAllowance.class, TokenAllowance.class)
                .map(this::createAllowances)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        // when
        migrate();

        // then
        assertThat(findAllAllowances()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private <C extends BaseAllowance> C clearOwner(C allowance) {
        allowance.setOwner(0);
        return allowance;
    }

    private <T extends BaseAllowance> List<BaseAllowance> createAllowances(Class<T> allowanceClass) {
        // good historical and current allowances
        T historicalAllowance = historicalAllowance(allowanceClass);
        T currentAllowance = current(historicalAllowance);
        persistAllowance(historicalAllowance);
        persistAllowance(currentAllowance);
        List<BaseAllowance> expected = new ArrayList<>(List.of(historicalAllowance, currentAllowance));

        // historical and current allowances with owner = 0
        historicalAllowance = clearOwner(historicalAllowance(allowanceClass));
        currentAllowance = current(historicalAllowance);
        persistAllowance(historicalAllowance);
        persistAllowance(currentAllowance);
        expected.addAll(List.of(fillOwner(historicalAllowance), fillOwner(currentAllowance)));

        return expected;
    }

    private <C extends BaseAllowance> C current(C historical) {
        return (C) historical.toBuilder().timestampRange(Range.atLeast(historical.getTimestampUpper())).build();
    }

    private <C extends BaseAllowance> C fillOwner(C allowance) {
        return (C) allowance.toBuilder().owner(allowance.getPayerAccountId()).build();
    }

    private Collection<BaseAllowance> findAllAllowances() {
        return Stream.of(CryptoAllowance.class, NftAllowance.class, TokenAllowance.class)
                .flatMap(cls -> Stream.of(findEntity(cls, TABLE_IDS), findHistory(cls, TABLE_IDS)))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private String getTable(History allowance) {
        String current = CASE_CONVERTER.convert(allowance.getClass().getSimpleName());
        return allowance.getTimestampRange().hasUpperBound() ? current + "_history" : current;
    }

    @SneakyThrows
    private <T extends BaseAllowance> T historicalAllowance(Class<T> allowanceClass) {
        T allowance = allowanceClass.newInstance();
        allowance.setOwner(id.getAndIncrement());
        allowance.setPayerAccountId(id.getAndIncrement());
        allowance.setSpender(id.getAndIncrement());
        allowance.setTimestampRange(Range.closedOpen(timestamp.getAndIncrement(), timestamp.getAndIncrement()));

        if (allowance instanceof CryptoAllowance) {
            ((CryptoAllowance) allowance).setAmount(10L);
        } else if (allowance instanceof NftAllowance) {
            NftAllowance nftAllowance = (NftAllowance) allowance;
            nftAllowance.setSerialNumbers(List.of(1L, 2L));
            nftAllowance.setTokenId(id.getAndIncrement());
        } else if (allowance instanceof TokenAllowance) {
            TokenAllowance tokenAllowance = (TokenAllowance) allowance;
            tokenAllowance.setAmount(15L);
            tokenAllowance.setTokenId(id.getAndIncrement());
        }

        return allowance;
    }

    private void persistAllowance(BaseAllowance allowance) {
        String table = getTable(allowance);
        String columns = "owner,payer_account_id,spender,timestamp_range";
        String values = "?,?,?,?::int8range";
        Object[] args = new Object[] {
                allowance.getOwner(),
                allowance.getPayerAccountId(),
                allowance.getSpender(),
                PostgreSQLGuavaRangeType.INSTANCE.asString(allowance.getTimestampRange())
        };
        Object[] extraArgs = new Object[]{};
        if (allowance instanceof CryptoAllowance) {
            columns += ",amount";
            values += ",?";
            CryptoAllowance cryptoAllowance = (CryptoAllowance) allowance;
            extraArgs = new Object[]{cryptoAllowance.getAmount()};
        } else if (allowance instanceof NftAllowance) {
            columns += ",approved_for_all,serial_numbers,token_id";
            values += ",?,?::bigint[],?";
            NftAllowance nftAllowance = (NftAllowance) allowance;
            extraArgs = new Object[]{
                    nftAllowance.isApprovedForAll(),
                    String.format("{%s}", StringUtils.join(nftAllowance.getSerialNumbers(), ",")),
                    nftAllowance.getTokenId()
            };
        } else if (allowance instanceof TokenAllowance) {
            columns += ",amount,token_id";
            values += ",?,?";
            TokenAllowance tokenAllowance = (TokenAllowance) allowance;
            extraArgs = new Object[]{
                    tokenAllowance.getAmount(),
                    tokenAllowance.getTokenId()
            };
        }

        args = ArrayUtils.addAll(args, extraArgs);
        String sql = String.format("insert into %s (%s) values (%s)", table, columns, values);
        jdbcOperations.update(sql, args);
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    private static class BaseAllowance implements History {
        private long owner;
        private long payerAccountId;
        private long spender;
        private Range<Long> timestampRange;
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    private static class CryptoAllowance extends BaseAllowance {
        private long amount;
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    private static class NftAllowance extends BaseAllowance {
        private boolean approvedForAll;
        private List<Long> serialNumbers;
        private long tokenId;
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    private static class TokenAllowance extends BaseAllowance {
        private long amount;
        private long tokenId;
    }
}
