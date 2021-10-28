package com.hedera.mirror.importer.parser.record.entity.sql;

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

import static com.hedera.mirror.importer.domain.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import java.util.List;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AssessedCustomFee;
import com.hedera.mirror.importer.domain.AssessedCustomFeeWrapper;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityType;
import com.hedera.mirror.importer.parser.PgCopy;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

class AssessedCustomFeePgCopyTest extends IntegrationTest {

    private static final long CONSENSUS_TIMESTAMP = 10L;
    private static final EntityId FEE_COLLECTOR_1 = EntityId.of("0.0.2000", EntityType.ACCOUNT);
    private static final EntityId FEE_COLLECTOR_2 = EntityId.of("0.0.2001", EntityType.ACCOUNT);
    private static final long FEE_PAYER_1 = 3000L;
    private static final EntityId FEE_PAYER_1_ID = EntityId.of(FEE_PAYER_1, EntityType.ACCOUNT);
    private static final long FEE_PAYER_2 = 3001L;
    private static final EntityId TOKEN_ID_1 = EntityId.of("0.0.5000", EntityType.TOKEN);
    private static final EntityId TOKEN_ID_2 = EntityId.of("0.0.5001", EntityType.TOKEN);

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Resource
    private DataSource dataSource;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private RecordParserProperties parserProperties;

    private PgCopy<AssessedCustomFee> assessedCustomFeePgCopy;

    @Test
    void copy() throws SQLException {
        // given
        assessedCustomFeePgCopy = new PgCopy<>(AssessedCustomFee.class, meterRegistry, parserProperties);

        // fee paid in HBAR with empty effective payer list
        AssessedCustomFee assessedCustomFee1 = new AssessedCustomFee();
        assessedCustomFee1.setAmount(10L);
        assessedCustomFee1.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_1, CONSENSUS_TIMESTAMP));
        assessedCustomFee1.setPayerAccountId(FEE_PAYER_1_ID);

        // fee paid in TOKEN_ID_1 by FEE_PAYER_1 to FEE_COLLECTOR_2
        AssessedCustomFee assessedCustomFee2 = new AssessedCustomFee();
        assessedCustomFee2.setAmount(20L);
        assessedCustomFee2.setEffectivePayerAccountIds(List.of(FEE_PAYER_1));
        assessedCustomFee2.setTokenId(TOKEN_ID_1);
        assessedCustomFee2.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_2, CONSENSUS_TIMESTAMP));
        assessedCustomFee2.setPayerAccountId(FEE_PAYER_1_ID);

        // fee paid in TOKEN_ID_2 by FEE_PAYER_1 and FEE_PAYER_2 to FEE_COLLECTOR_2
        AssessedCustomFee assessedCustomFee3 = new AssessedCustomFee();
        assessedCustomFee3.setAmount(30L);
        assessedCustomFee3.setEffectivePayerAccountIds(List.of(FEE_PAYER_1, FEE_PAYER_2));
        assessedCustomFee3.setTokenId(TOKEN_ID_2);
        assessedCustomFee3.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_2, CONSENSUS_TIMESTAMP));
        assessedCustomFee3.setPayerAccountId(EntityId.of(FEE_PAYER_2, ACCOUNT));

        List<AssessedCustomFee> assessedCustomFees = List.of(
                assessedCustomFee1,
                assessedCustomFee2,
                assessedCustomFee3
        );

        // when
        assessedCustomFeePgCopy.copy(assessedCustomFees, dataSource.getConnection());

        // then
        List<AssessedCustomFeeWrapper> actual = jdbcTemplate.query(AssessedCustomFeeWrapper.SELECT_QUERY,
                AssessedCustomFeeWrapper.ROW_MAPPER);
        assertThat(actual)
                .map(AssessedCustomFeeWrapper::getAssessedCustomFee)
                .containsExactlyInAnyOrderElementsOf(assessedCustomFees);
    }
}
