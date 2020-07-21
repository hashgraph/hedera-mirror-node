package com.hedera.mirror.importer.parser.record.entity.sql;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;

class PgCopyTest extends IntegrationTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Resource
    private DataSource dataSource;
    @Resource
    private CryptoTransferRepository cryptoTransferRepository;

    private PgCopy<CryptoTransfer> cryptoTransferPgCopy;

    @BeforeEach
    void beforeEach() throws Exception {
        cryptoTransferPgCopy = new PgCopy<>(dataSource, CryptoTransfer.class, meterRegistry);
    }

    @Test
    void testCopy() {
        var cryptoTransfers = new HashSet<CryptoTransfer>();
        cryptoTransfers.add(cryptoTransfer(1));
        cryptoTransfers.add(cryptoTransfer(2));
        cryptoTransfers.add(cryptoTransfer(3));

        cryptoTransferPgCopy.copy(cryptoTransfers);

        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(cryptoTransfers);
    }

    @Test
    void throwsParserException() throws SQLException, IOException {
        // given
        CopyManager copyManager = mock(CopyManager.class);
        doThrow(SQLException.class).when(copyManager).copyIn(any(), (Reader) any());
        PGConnection pgConnection = mock(PGConnection.class);
        doReturn(copyManager).when(pgConnection).getCopyAPI();
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        doReturn(conn).when(dataSource).getConnection();
        doReturn(pgConnection).when(conn).unwrap(any());
        var cryptoTransferPgCopy2 = new PgCopy<>(dataSource, CryptoTransfer.class, meterRegistry);
        var cryptoTransfers = new HashSet<CryptoTransfer>();
        cryptoTransfers.add(cryptoTransfer(1));
        // when
        assertThatThrownBy(() -> cryptoTransferPgCopy2.copy(cryptoTransfers))
                .isInstanceOf(ParserException.class);
    }

    @Test
    void testNullItems() {
        cryptoTransferPgCopy.copy(null);
        assertThat(cryptoTransferRepository.count()).isEqualTo(0);
    }

    private CryptoTransfer cryptoTransfer(long consensusTimestamp) {
        return new CryptoTransfer(consensusTimestamp, 1L, EntityId.of(0L, 1L, 2L, EntityTypeEnum.ACCOUNT));
    }
}
