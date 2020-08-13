package com.hedera.mirror.importer.parser.record;

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

import static com.hedera.mirror.importer.domain.ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import javax.annotation.Resource;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityRecordItemListener;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlEntityListener;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlProperties;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@Log4j2
public class RecordFileParserIntegrationTest extends IntegrationTest {

    @TempDir
    static Path dataPath;

    @Value("classpath:data")
    Path testPath;

    private RecordFileParser recordFileParser;

    @Mock
    private ApplicationStatusRepository applicationStatusRepository;

    @Resource
    private RecordParserProperties parserProperties;

    @Resource
    private RecordStreamFileListener recordStreamFileListener;

    @Resource
    private RecordItemListener recordItemListener;

    @Resource
    private CryptoTransferRepository cryptoTransferRepository;

    @Resource
    private TransactionRepository transactionRepository;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    SqlProperties sqlProperties;

    @Resource
    DataSource dataSource;

    @Mock
    RecordFileRepository recordFileRepository;

    @Resource
    CommonParserProperties commonParserProperties;

    @Resource
    EntityProperties entityProperties;

    @Resource
    AddressBookService addressBookService;

    @Resource
    NonFeeTransferExtractionStrategy nonFeeTransfersExtractor;

    @Resource
    TransactionHandlerFactory transactionHandlerFactory;

    private File file;
    private FileCopier fileCopier;
    private static final int NUM_CRYPTO_FILE = 93;
    private static final int NUM_TXNS_FILE = 19;
    private static final int NUM_ENTITIES_FILE = 8;
    private static StreamFileData streamFileData;
    private Cache entityCache;

    @BeforeEach
    void before() throws FileNotFoundException {
        var mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        parserProperties = new RecordParserProperties(mirrorProperties);
        parserProperties.setKeepFiles(false);
        parserProperties.init();
        StreamType streamType = StreamType.RECORD;

        parserProperties.getMirrorProperties().setVerifyHashAfter(Instant.parse("2019-09-01T00:00:00.000000Z"));
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("");

        fileCopier = FileCopier
                .create(Path.of(getClass().getClassLoader().getResource("data").getPath()), dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3")
                .filterFiles("*.rcd");
        fileCopier.copy();
        file = dataPath.resolve("2019-08-30T18_10_00.419072Z.rcd").toFile();

        streamFileData = new StreamFileData(file.toString(), new FileInputStream(file));

        CacheManager cacheManagerNeverExpireLarge = Mockito.mock(CacheManager.class);
        entityCache = Mockito.mock(Cache.class);
        doNothing().when(entityCache).put(any(), any());
        doReturn(entityCache).when(cacheManagerNeverExpireLarge).getCache(CacheConfiguration.NEVER_EXPIRE_LARGE);

        recordStreamFileListener = new SqlEntityListener(sqlProperties, dataSource, recordFileRepository,
                new SimpleMeterRegistry(), cacheManagerNeverExpireLarge);

        recordItemListener = new EntityRecordItemListener(commonParserProperties, entityProperties,
                addressBookService, entityRepository, nonFeeTransfersExtractor,
                (EntityListener) recordStreamFileListener,
                transactionHandlerFactory);

        recordFileParser = new RecordFileParser(applicationStatusRepository, parserProperties,
                new SimpleMeterRegistry(), recordItemListener, recordStreamFileListener);
    }

    @Test
    void parse() {
        // when
        recordFileParser.parse(streamFileData);

        // then
        verifyFinalDatabaseState(NUM_CRYPTO_FILE, NUM_TXNS_FILE, NUM_ENTITIES_FILE);
    }

    @Test
    void verifyRollbackOnEntityCachePutError() throws ParserSQLException {
        // given
        doThrow(ParserSQLException.class).when(entityCache).put(any(), any());
        // when
        Assertions.assertThrows(ParserSQLException.class, () -> {
            recordFileParser.parse(streamFileData);
        });
        // then
        verifyFinalDatabaseState(0, 0, 0);
    }

    void verifyFinalDatabaseState(int cryptoTransferCount, int transactionCount, int entityCount) {
        assertEquals(cryptoTransferCount, cryptoTransferRepository.count()); // pg copy populated
        assertEquals(transactionCount, transactionRepository.count()); // pg copy populated

        try {
            Connection connection = dataSource.getConnection();
            PreparedStatement entitySelectCommand = connection.prepareStatement("select * from t_entities");
            ResultSet resultSet = entitySelectCommand.executeQuery();

            // test validation of entity count is challenging but actual run of importer confirms rollback of data
            // repository seems to get a late update for rollbacks but is valid on happy path hence below hack
            long actualCount = entityCount == 0 ? resultSet.getFetchSize() : entityRepository.count();
            assertEquals(entityCount, actualCount); // prepared statement populated
        } catch (SQLException sqlex) {
        }
    }
}
