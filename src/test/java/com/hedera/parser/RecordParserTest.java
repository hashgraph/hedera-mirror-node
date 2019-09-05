package com.hedera.parser;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.hedera.FileCopier;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.databaseUtilities.ApplicationStatus;
import com.hedera.databaseUtilities.DatabaseUtilities;
import com.hedera.utilities.Utility;
import io.findify.s3mock.S3Mock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.*;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RecordParserTest {

    @Mock
    private ApplicationStatus applicationStatus;

    @TempDir
    Path dataPath;

    @TempDir
    Path s3Path;

    private Path validPath;
    private S3Mock s3;
    private FileCopier fileCopier;
    private RecordFileParser recordFileParser;

    @BeforeEach
    void before() throws Exception {
        ConfigLoader.setDownloadToDir(dataPath.toAbsolutePath().toString());
        ConfigLoader.setMaxDownloadItems(100);

        deleteDatabaseData();
        
        recordFileParser = new RecordFileParser();
        recordFileParser.applicationStatus = applicationStatus;

        validPath = Paths.get(ConfigLoader.getDefaultParseDir(ConfigLoader.OPERATION_TYPE.RECORDS));

        when(applicationStatus.getLastProcessedRecordHash()).thenReturn("");
    }

    @AfterEach
    void after() {
    }

    @Test
    @Tag("IntegrationTest")
    @Order(1)
    @DisplayName("Parse record files")
    void parseRecordFiles() throws Exception {
    	
    	fileCopier = FileCopier.create(Utility.getResource("data").toPath(), s3Path)
                .from("recordstreams", "transactionTesting")
                .to(validPath);
        fileCopier.copy();
		recordFileParser.parseNewFiles(validPath.toString());
		
    }
    @Test
    @Tag("IntegrationTest")
    @Order(2)
    @DisplayName("Parse record files - check counts")
    void parseRecordFilesCheckCounts() throws Exception {
    	// connect to database, read counts, assert values
    	// file count
    	// transaction count
    	// entities count
    	// file data count
    	// contract result count
    }
        
    @Test
    @Tag("IntegrationTest")
    @Order(3)
    @DisplayName("Parse record files - check crypto")
    void parseRecordFilesCheckCrypto() throws Exception {
    	// connect to database, read data, assert values
    }
    
    @Test
    @Tag("IntegrationTest")
    @Order(4)
    @DisplayName("Parse record files - check files")
    void parseRecordFilesCheckFiles() throws Exception {
    	// connect to database, read data, assert values
    }

    @Test
    @Tag("IntegrationTest")
    @Order(5)
    @DisplayName("Parse record files - check contracts")
    void parseRecordFilesCheckContracts() throws Exception {
    	// connect to database, read data, assert values
    }

    private static void deleteDatabaseData() throws SQLException {
        Connection connect = null;
        try {
        	connect = DatabaseUtilities.openDatabase(connect);

        	connect.createStatement().execute("DELETE FROM t_record_files");
        	connect.createStatement().execute("DELETE FROM t_record_files");
        	connect.createStatement().execute("DELETE FROM t_file_data");
        	connect.createStatement().execute("DELETE FROM t_contract_result");
        	connect.createStatement().execute("DELETE FROM t_livehashes");
        	connect.createStatement().execute("DELETE FROM t_cryptotransferlists");
        	connect.createStatement().execute("DELETE FROM t_transactions");
        	connect.createStatement().execute("DELETE FROM t_entities");
        	connect.createStatement().execute("DELETE FROM t_events");
        	connect.createStatement().execute("DELETE FROM account_balance_sets");
        	connect.createStatement().execute("DELETE FROM account_balances");
        	connect.createStatement().execute("UPDATE t_application_status SET status_value = ''");
        } catch (Exception e) {
        	if (connect != null) {
        		connect.close();
        	}
            throw new RuntimeException(e);
        }
    }
}
