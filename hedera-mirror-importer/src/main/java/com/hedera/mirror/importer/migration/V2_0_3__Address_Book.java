package com.hedera.mirror.importer.migration;

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

import com.google.common.base.Stopwatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.addressbook.AddressBookServiceImpl;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.util.EntityIdEndec;

@Log4j2
@Named
public class V2_0_3__Address_Book extends BaseJavaMigration {
    private final AddressBookService addressBookService;
    private final MirrorProperties mirrorProperties;
    private final DataSource dataSource;
    private final String FILE_DATA_SQL = "select * from file_data where consensus_timestamp > ? and entity_id " +
            "in (101, 102) order by consensus_timestamp asc limit ?";
    private JdbcTemplate jdbcTemplate;

    public V2_0_3__Address_Book(MirrorProperties mirrorProperties, @Lazy AddressBookService addressBookService,
                                DataSource dataSource) {
        this.addressBookService = addressBookService;
        this.mirrorProperties = mirrorProperties;
        this.dataSource = dataSource;
    }

    @Override
    public void migrate(Context context) throws Exception {
        jdbcTemplate = new JdbcTemplate(dataSource);
        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicLong currentConsensusTimestamp = new AtomicLong(0);
        AtomicLong fileDataEntries = new AtomicLong(0);
        byte[] addressBookBytes;

        // retrieve bootstrap address book from filesystem or classpath
        try {
            Path initialAddressBook = mirrorProperties.getInitialAddressBook();
            if (initialAddressBook != null) {
                addressBookBytes = Files.readAllBytes(initialAddressBook);
                log.info("Loading bootstrap address book of {} B from {}", addressBookBytes.length,
                        initialAddressBook);
            } else {
                MirrorProperties.HederaNetwork hederaNetwork = mirrorProperties.getNetwork();
                String resourcePath = String.format("/addressbook/%s", hederaNetwork.name().toLowerCase());
                Resource resource = new ClassPathResource(resourcePath, getClass());
                addressBookBytes = IOUtils.toByteArray(resource.getInputStream());
                log.info("Loading bootstrap address book of {} B from {}", addressBookBytes.length, resource);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load bootstrap address book", e);
        }

        FileData bootStrapFileData = new FileData(0L, addressBookBytes,
                AddressBookServiceImpl.ADDRESS_BOOK_102_ENTITY_ID,
                TransactionTypeEnum.FILECREATE.getProtoId());
        addressBookService.update(bootStrapFileData);
        fileDataEntries.incrementAndGet();

        // starting from consensusTimeStamp = 0 retrieve pages of fileData entries for historic address books
        int pageSize = 1000; // option to parameterize this
        List<FileData> fileDataList = getLatestFileData(currentConsensusTimestamp.get(), pageSize);
        while (!CollectionUtils.isEmpty(fileDataList)) {
            fileDataList.forEach(fileData -> {
                // call normal address book file transaction parsing flow to parse and ingest address book contents
                addressBookService.update(fileData);
                fileDataEntries.incrementAndGet();
                currentConsensusTimestamp.set(fileData.getConsensusTimestamp());
            });

            fileDataList = getLatestFileData(currentConsensusTimestamp.get(), pageSize);
        }
        log.info("Migration processed {} in {} ms", fileDataEntries.get(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private List<FileData> getLatestFileData(long consensusTimestamp, int pageSize) throws SQLException {
        log.info("Retrieve file_data rows after {} ns with page size {}", consensusTimestamp, pageSize);
        List<FileData> fileDataList = jdbcTemplate.query(
                FILE_DATA_SQL,
                new Object[] {consensusTimestamp, pageSize},
                new RowMapper<>() {
                    @Override
                    public FileData mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return new FileData(rs.getLong("consensus_timestamp"),
                                rs.getBytes("file_data"),
                                EntityIdEndec.decode(rs.getInt("entity_id"), EntityTypeEnum.FILE),
                                rs.getInt("transaction_type"));
                    }
                });

        log.info("Retrieved {} file_data rows for address book processing", fileDataList.size());
        return fileDataList;
    }
}
