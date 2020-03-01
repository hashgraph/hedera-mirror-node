package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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

import java.sql.Connection;
import java.util.Optional;
import javax.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.NonFeeTransferRepository;
import com.hedera.mirror.importer.util.DatabaseUtilities;

@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
public class PostgresWritingRecordParserItemHandlerTest extends IntegrationTest {

    @Resource
    protected CryptoTransferRepository cryptoTransferRepository;

    @Resource
    protected NonFeeTransferRepository nonFeeTransferRepository;

    @Resource
    protected PostgresWritingRecordParsedItemHandler postgresWriter;

    protected Connection connection;

    @BeforeEach
    final void beforeEach() throws Exception {
        connection = DatabaseUtilities.getConnection();
        connection.setAutoCommit(false);
        postgresWriter.initSqlStatements(connection);
    }

    @AfterEach
    final void afterEach() {
        postgresWriter.finish();
    }

    void completeFileAndCommit() throws Exception {
        postgresWriter.onFileComplete();
        connection.commit();
    }

    @Test
    void onCryptoTransferList() throws Exception {
        // setup
        CryptoTransfer cryptoTransfer1 = new CryptoTransfer(1L, 1L, 0L, 1L);
        CryptoTransfer cryptoTransfer2 = new CryptoTransfer(2L, -2L, 0L, 2L);

        // when
        postgresWriter.onCryptoTransferList(cryptoTransfer1);
        postgresWriter.onCryptoTransferList(cryptoTransfer2);
        completeFileAndCommit();

        // expect
        assertEquals(2, cryptoTransferRepository.count());
        assertExistsAndEquals(cryptoTransferRepository, cryptoTransfer1, 1L);
        assertExistsAndEquals(cryptoTransferRepository, cryptoTransfer2, 2L);
    }

    @Test
    void onNonFeeTransfer() throws Exception {
        // setup
        NonFeeTransfer nonFeeTransfer1 = new NonFeeTransfer(1L, 1L, 0L, 1L);
        NonFeeTransfer nonFeeTransfer2 = new NonFeeTransfer(2L, -2L, 0L, 2L);

        // when
        postgresWriter.onNonFeeTransfer(nonFeeTransfer1);
        postgresWriter.onNonFeeTransfer(nonFeeTransfer2);
        completeFileAndCommit();

        // expect
        assertEquals(2, nonFeeTransferRepository.count());
        assertExistsAndEquals(nonFeeTransferRepository, nonFeeTransfer1, 1L);
        assertExistsAndEquals(nonFeeTransferRepository, nonFeeTransfer2, 2L);
    }

    @Test
    void rollback() throws Exception {
        // when
        postgresWriter.onNonFeeTransfer(new NonFeeTransfer(1L, 1L, 0L, 1L));
        postgresWriter.onCryptoTransferList(new CryptoTransfer(2L, -2L, 0L, 2L));
        connection.rollback();

        // expect
        assertEquals(0, nonFeeTransferRepository.count());
        assertEquals(0, cryptoTransferRepository.count());
    }

    static <T, ID> void assertExistsAndEquals(CrudRepository<T, ID> repository, T expected, ID id) throws Exception {
        Optional<T> actual = repository.findById(id);
        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }
}
