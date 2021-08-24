package com.hedera.mirror.importer.parser;

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

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.util.concurrent.testing.TestingExecutors;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceUtils;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

class DbConnectionUtilsTest extends IntegrationTest {

    private Connection connection;

    private PgCopy<CryptoTransfer> cryptoTransferPgCopy;

    @Resource
    private DataSource dataSource;

    private ScheduledExecutorService executor;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Resource
    private RecordParserProperties parserProperties;

    @BeforeEach
    void setUp() throws SQLException {
        connection = dataSource.getConnection();
        cryptoTransferPgCopy = new PgCopy<>(CryptoTransfer.class, meterRegistry, parserProperties);
    }

    @AfterEach
    void tearDown() {
        DataSourceUtils.releaseConnection(connection, dataSource);
        executor.shutdown();
    }

    @Test
    void timeout() {
        // given
        List<CryptoTransfer> cryptoTransfers = cryptoTransfers();
        executor = TestingExecutors.sameThreadScheduledExecutor();
        DbConnectionUtils.scheduleAbort(connection, executor, Duration.ofMillis(100));

        // when, then
        assertThrows(Exception.class, () -> cryptoTransferPgCopy.copy(cryptoTransfers, connection));
    }

    private List<CryptoTransfer> cryptoTransfers() {
        return LongStream.range(0, 100)
                .mapToObj(value -> new CryptoTransfer(value, 100L, EntityId.of(0, 0, value + 1, EntityTypeEnum.ACCOUNT)))
                .collect(Collectors.toList());
    }
}
