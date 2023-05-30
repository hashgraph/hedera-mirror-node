/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.addressbook.AddressBookServiceImpl;
import com.hedera.mirror.importer.repository.AddressBookEntryRepository;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRecordItemListenerFileTest extends AbstractEntityRecordItemListenerTest {

    private static final FileID ADDRESS_BOOK_FILEID = FileID.newBuilder()
            .setShardNum(0)
            .setRealmNum(0)
            .setFileNum(AddressBookServiceImpl.FILE_102.getEntityNum())
            .build();
    private static final FileID FILE_ID =
            FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(1001).build();
    private static final byte[] FILE_CONTENTS = {'a', 'b', 'c'};
    private static final int TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT = 4;

    private final AddressBookRepository addressBookRepository;
    private final AddressBookEntryRepository addressBookEntryRepository;
    private final FileDataRepository fileDataRepository;
    private final AddressBookService addressBookService;

    @Value("classpath:addressbook/mainnet")
    private final File addressBookLarge;

    @Value("classpath:addressbook/testnet")
    private final File addressBookSmall;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @Test
    void fileCreate() {
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityAndData(transactionBody.getFileCreate(), record.getConsensusTimestamp()));
    }

    @Test
    void fileCreateDoNotPersist() {
        entityProperties.getPersist().setFiles(false);
        entityProperties.getPersist().setSystemFiles(false);
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccessNoData(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, record));
    }

    @Test
    void fileCreatePersistSystemPositive() {
        entityProperties.getPersist().setFiles(false);
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileID fileID =
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(10).build();
        TransactionRecord record = transactionRecord(transactionBody, fileID);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(fileID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityAndData(transactionBody.getFileCreate(), record.getConsensusTimestamp()));
    }

    @Test
    void fileCreatePersistSystemNegative() {
        entityProperties.getPersist().setFiles(false);
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileID fileID = FileID.newBuilder()
                .setShardNum(0)
                .setRealmNum(0)
                .setFileNum(2000)
                .build();
        TransactionRecord record = transactionRecord(transactionBody, fileID);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccessNoData(fileID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntity(transactionBody.getFileCreate(), record.getConsensusTimestamp()));
    }

    @ParameterizedTest(name = "with {0} s and expected {1} ns")
    @CsvSource({
        "9223372036854775807, 9223372036854775807",
        "31556889864403199, 9223372036854775807",
        "-9223372036854775808, -9223372036854775808",
        "-1000000000000000000, -9223372036854775808"
    })
    void fileCreateExpirationTimeOverflow(long seconds, long expectedNanosTimestamp) {
        Transaction transaction =
                fileCreateTransaction(Timestamp.newBuilder().setSeconds(seconds).build());
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        var dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());
        assertEquals(expectedNanosTimestamp, dbAccountEntity.getExpirationTimestamp());
    }

    @Test
    void fileAppend() {
        Transaction transaction = fileAppendTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCount(1, 3, 1),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileData(transactionBody.getFileAppend().getContents(), record.getConsensusTimestamp()));
    }

    @Test
    void fileAppendToSystemFile() {
        FileID fileID =
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(10).build();
        Transaction transaction = fileAppendTransaction(fileID, FILE_CONTENTS);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody, fileID);

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCount(1, 3, 1),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileData(transactionBody.getFileAppend().getContents(), record.getConsensusTimestamp()));
    }

    @Test
    void fileUpdateAllToExisting() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileUpdateAllTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnTwoFileTransactions(),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityAndData(transactionBody.getFileUpdate(), record.getConsensusTimestamp()));
    }

    @Test
    void fileUpdateAllToExistingFailedTransaction() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileUpdateAllTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(FILE_ID)),
                () -> assertEquals(6, cryptoTransferRepository.count()),
                () -> assertEquals(1, fileDataRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntity(createTransactionBody.getFileCreate(), record.getConsensusTimestamp()));
    }

    @Test
    @Transactional
    void fileAppendToAddressBook() throws IOException {
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        byte[] addressBook = FileUtils.readFileToByteArray(addressBookLarge);
        byte[] addressBookUpdate = Arrays.copyOf(addressBook, 6144);
        byte[] addressBookAppend = Arrays.copyOfRange(addressBook, 6144, addressBook.length);

        // Initial address book update
        Transaction transactionUpdate = fileUpdateAllTransaction(ADDRESS_BOOK_FILEID, addressBookUpdate);
        TransactionBody transactionBodyUpdate = getTransactionBody(transactionUpdate);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBodyUpdate.getFileUpdate();
        TransactionRecord recordUpdate = transactionRecord(transactionBodyUpdate, ADDRESS_BOOK_FILEID);

        // Address book append
        Transaction transactionAppend = fileAppendTransaction(ADDRESS_BOOK_FILEID, addressBookAppend);
        TransactionBody transactionBodyAppend = getTransactionBody(transactionAppend);
        FileAppendTransactionBody fileAppendTransactionBody = transactionBodyAppend.getFileAppend();
        TransactionRecord recordAppend = transactionRecord(transactionBodyAppend, ADDRESS_BOOK_FILEID);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordUpdate)
                .transaction(transactionUpdate)
                .build());
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordAppend)
                .transaction(transactionAppend)
                .build());

        // verify current address book is updated
        AddressBook newAddressBook = addressBookService.getCurrent();
        assertAll(
                () -> assertThat(newAddressBook.getStartConsensusTimestamp())
                        .isEqualTo(DomainUtils.timeStampInNanos(recordAppend.getConsensusTimestamp()) + 1),
                () -> assertThat(newAddressBook.getEntries())
                        .describedAs("Should overwrite address book with new update")
                        .hasSize(13),
                () -> assertArrayEquals(addressBook, newAddressBook.getFileData()));

        assertAll(
                () -> assertRowCountOnAddressBookTransactions(),
                () -> assertTransactionAndRecord(transactionBodyUpdate, recordUpdate),
                () -> assertTransactionAndRecord(transactionBodyAppend, recordAppend),
                () -> assertFileData(fileAppendTransactionBody.getContents(), recordAppend.getConsensusTimestamp()),
                () -> assertFileData(fileUpdateTransactionBody.getContents(), recordUpdate.getConsensusTimestamp()),
                () -> assertAddressBookData(addressBook, recordAppend.getConsensusTimestamp()),
                () -> assertEquals(13 + TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count()),
                () -> assertEquals(2, addressBookRepository.count()),
                () -> assertEquals(2, fileDataRepository.count()) // update and append
                );
    }

    @Test
    @Transactional
    void fileAppendToAddressBookInSingleRecordFile() throws IOException {
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        byte[] addressBookCreate = Arrays.copyOf(FileUtils.readFileToByteArray(addressBookSmall), 6144);

        // Initial address book create
        Transaction transactionCreate = fileUpdateAllTransaction(ADDRESS_BOOK_FILEID, addressBookCreate);
        TransactionBody transactionBodyCreate = getTransactionBody(transactionCreate);
        TransactionRecord recordCreate = transactionRecord(transactionBodyCreate, ADDRESS_BOOK_FILEID);
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(transactionCreate)
                .build());

        assertAll(
                () -> assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count()),
                () -> assertEquals(1, addressBookRepository.count()),
                () -> assertEquals(1, fileDataRepository.count()) // update and append
                );

        byte[] addressBook = FileUtils.readFileToByteArray(addressBookLarge);
        byte[] addressBookUpdate = Arrays.copyOf(addressBook, 6144);
        byte[] addressBookAppend = Arrays.copyOfRange(addressBook, 6144, addressBook.length);

        // Initial address book update
        Transaction transactionUpdate = fileUpdateAllTransaction(ADDRESS_BOOK_FILEID, addressBookUpdate);
        TransactionBody transactionBodyUpdate = getTransactionBody(transactionUpdate);
        TransactionRecord recordUpdate = transactionRecord(transactionBodyUpdate, ADDRESS_BOOK_FILEID);

        // Address book append
        Transaction transactionAppend = fileAppendTransaction(ADDRESS_BOOK_FILEID, addressBookAppend);
        TransactionBody transactionBodyAppend = getTransactionBody(transactionAppend);
        TransactionRecord recordAppend = transactionRecord(transactionBodyAppend, ADDRESS_BOOK_FILEID);

        parseRecordItemsAndCommit(List.of(
                RecordItem.builder()
                        .transactionRecord(recordUpdate)
                        .transaction(transactionUpdate)
                        .build(),
                RecordItem.builder()
                        .transactionRecord(recordAppend)
                        .transaction(transactionAppend)
                        .build()));

        // verify current address book is updated
        AddressBook newAddressBook = addressBookService.getCurrent();
        assertAll(
                () -> assertThat(newAddressBook.getStartConsensusTimestamp())
                        .isEqualTo(DomainUtils.timeStampInNanos(recordAppend.getConsensusTimestamp()) + 1),
                () -> assertThat(newAddressBook.getEntries())
                        .describedAs("Should overwrite address book with new update")
                        .hasSize(13),
                () -> assertArrayEquals(addressBook, newAddressBook.getFileData()),
                () -> assertAddressBookData(addressBook, recordAppend.getConsensusTimestamp()),
                () -> assertEquals(13 + TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count()),
                () -> assertEquals(2, addressBookRepository.count()),
                () -> assertEquals(3, fileDataRepository.count()) // update and append
                );
    }

    @Test
    void fileUpdateAllToNew() {
        Transaction transaction = fileUpdateAllTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityAndData(transactionBody.getFileUpdate(), record.getConsensusTimestamp()));
    }

    @Test
    void fileUpdateContentsToExisting() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);

        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileUpdateContentsTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        Entity actualFile = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnTwoFileTransactions(),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileData(transactionBody.getFileUpdate().getContents(), record.getConsensusTimestamp()),
                // Additional entity checks
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertNotNull(actualFile.getExpirationTimestamp()),
                () -> assertNotNull(actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    @Test
    void fileUpdateContentsToNew() {
        Transaction transaction = fileUpdateContentsTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        Entity actualFile = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnSuccess(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileData(transactionBody.getFileUpdate().getContents(), record.getConsensusTimestamp()),
                // Additional entity checks
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertNull(actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    @Test
    void fileUpdateExpiryToExisting() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileUpdateExpiryTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        Entity actualFile = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                // TODO: Review row count of fileDataRepository with issue #294, probably should be 1
                () -> assertRowCountOnTwoFileTransactions(),
                () -> assertTransactionAndRecord(transactionBody, record),
                // Additional entity checks
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertEquals(
                        DomainUtils.timeStampInNanos(
                                transactionBody.getFileUpdate().getExpirationTime()),
                        actualFile.getExpirationTimestamp()),
                () -> assertNotNull(actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    @Test
    void fileUpdateExpiryToNew() {
        Transaction transaction = fileUpdateExpiryTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        Entity actualFile = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                // TODO: Review row count in fileDataRepository with issue #294, probably should be 0
                () -> assertRowCountOnSuccess(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, record),
                // Additional entity checks
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertEquals(
                        DomainUtils.timeStampInNanos(
                                transactionBody.getFileUpdate().getExpirationTime()),
                        actualFile.getExpirationTimestamp()),
                () -> assertNull(actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    @Test
    void fileUpdateKeysToExisting() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileUpdateKeysTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        Entity dbFileEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                // TODO: Review row count of fileDataRepository with issue #294, probably should be 1
                () -> assertRowCountOnTwoFileTransactions(),
                () -> assertTransactionAndRecord(transactionBody, record),
                // Additional entity checks
                () -> assertFalse(dbFileEntity.getDeleted()),
                () -> assertNotNull(dbFileEntity.getExpirationTimestamp()),
                () -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.getKey()),
                () -> assertNull(dbFileEntity.getAutoRenewPeriod()),
                () -> assertNull(dbFileEntity.getProxyAccountId()));
    }

    @Test
    void fileUpdateKeysToNew() {
        Transaction transaction = fileUpdateKeysTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        Entity dbFileEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                // TODO: Review row count in fileDataRepository with issue #294, probably should be 0
                () -> assertRowCountOnSuccess(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, record),
                // Additional entity checks
                () -> assertFalse(dbFileEntity.getDeleted()),
                () -> assertNull(dbFileEntity.getExpirationTimestamp()),
                () -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.getKey()),
                () -> assertNull(dbFileEntity.getAutoRenewPeriod()),
                () -> assertNull(dbFileEntity.getProxyAccountId()));
    }

    @Test
    void fileUpdateAllToNewSystem() {
        FileID fileID =
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(10).build();
        Transaction transaction = fileUpdateAllTransaction(fileID, FILE_CONTENTS);
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord record = transactionRecord(transactionBody, fileID);

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(fileID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityAndData(fileUpdateTransactionBody, record.getConsensusTimestamp()));
    }

    @Test
    void fileUpdateAddressBookPartial() throws IOException {
        byte[] largeAddressBook = FileUtils.readFileToByteArray(addressBookLarge);
        byte[] addressBookUpdate = Arrays.copyOf(largeAddressBook, largeAddressBook.length / 2);
        Transaction transaction = fileUpdateAllTransaction(ADDRESS_BOOK_FILEID, addressBookUpdate);
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord record = transactionRecord(transactionBody, ADDRESS_BOOK_FILEID);

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        addressBookService.getCurrent();
        assertAll(
                () -> assertRowCountOnSuccess(ADDRESS_BOOK_FILEID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityAndData(fileUpdateTransactionBody, record.getConsensusTimestamp()),
                () -> assertEquals(1, addressBookRepository.count()),
                () -> assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count()),
                () -> assertEquals(1, fileDataRepository.count()));
    }

    @Test
    @Transactional
    void fileUpdateAddressBookComplete() throws IOException {
        byte[] addressBook = FileUtils.readFileToByteArray(addressBookSmall);
        assertThat(addressBook).hasSizeLessThan(6144);
        Transaction transaction = fileUpdateAllTransaction(ADDRESS_BOOK_FILEID, addressBook);
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord record = transactionRecord(transactionBody, ADDRESS_BOOK_FILEID);

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        // verify current address book is changed
        AddressBook currentAddressBook = addressBookService.getCurrent();
        assertAll(
                () -> assertRowCountOnSuccess(ADDRESS_BOOK_FILEID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityAndData(fileUpdateTransactionBody, record.getConsensusTimestamp()),
                () -> assertAddressBookData(addressBook, record.getConsensusTimestamp()),
                () -> assertThat(currentAddressBook.getStartConsensusTimestamp())
                        .isEqualTo(DomainUtils.timeStampInNanos(record.getConsensusTimestamp()) + 1),
                () -> assertThat(currentAddressBook.getEntries()).hasSize(4),
                () -> assertEquals(2, addressBookRepository.count()),
                () -> assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + 4, addressBookEntryRepository.count()),
                () -> assertEquals(1, fileDataRepository.count()));
    }

    @Test
    void fileUpdateFeeSchedule() {
        FileID fileId = FileID.newBuilder().setFileNum(111L).build();
        Transaction transaction = fileUpdateAllTransaction(fileId, FILE_CONTENTS);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(fileId),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityAndData(transactionBody.getFileUpdate(), record.getConsensusTimestamp()));
    }

    @Test
    void fileUpdateThrottles() {
        FileID fileId = FileID.newBuilder().setFileNum(123L).build();
        Transaction transaction = fileUpdateAllTransaction(fileId, FILE_CONTENTS);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record =
                transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(fileId),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityAndData(transactionBody.getFileUpdate(), record.getConsensusTimestamp()));
    }

    @Test
    void fileDeleteToExisting() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(transaction)
                .build());

        Entity dbFileEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(FILE_ID)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(6, cryptoTransferRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(1, fileDataRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                // Additional entity checks
                () -> assertTrue(dbFileEntity.getDeleted()),
                () -> assertNotNull(dbFileEntity.getKey()),
                () -> assertNotNull(dbFileEntity.getExpirationTimestamp()),
                () -> assertNull(dbFileEntity.getAutoRenewPeriod()),
                () -> assertNull(dbFileEntity.getProxyAccountId()));
    }

    @Test
    void fileDeleteToNew() {
        Transaction fileDeleteTransaction = fileDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(fileDeleteTransaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(fileDeleteTransaction)
                .build());
        Entity fileEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnSuccessNoData(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityHasNullFields(record.getConsensusTimestamp()),
                () -> assertTrue(fileEntity.getDeleted()));
    }

    @Test
    void fileDeleteFailedTransaction() {
        Transaction fileDeleteTransaction = fileDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(fileDeleteTransaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(fileDeleteTransaction)
                .build());

        assertAll(() -> assertRowCountOnFailureNoData(), () -> assertFailedFileTransaction(transactionBody, record));
    }

    @Test
    void fileSystemDeleteTransaction() {
        Transaction systemDeleteTransaction = systemDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(systemDeleteTransaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(systemDeleteTransaction)
                .build());
        Entity fileEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnSuccessNoData(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(fileEntity)
                        .isNotNull()
                        .extracting(Entity::getDeleted)
                        .isEqualTo(true));
    }

    @Test
    void fileSystemUnDeleteTransaction() {
        Transaction systemUndeleteTransaction = systemUnDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(systemUndeleteTransaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(systemUndeleteTransaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccessNoData(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFileEntityHasNullFields(record.getConsensusTimestamp()));
    }

    @Test
    void fileSystemDeleteInvalidTransaction() {
        Transaction systemDeleteTransaction = systemDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(systemDeleteTransaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(systemDeleteTransaction)
                .build());

        assertFailedFileTransaction(transactionBody, record);
    }

    @Test
    void fileSystemUnDeleteFailedTransaction() {
        Transaction systemUndeleteTransaction = systemUnDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(systemUndeleteTransaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(record)
                .transaction(systemUndeleteTransaction)
                .build());

        assertFailedFileTransaction(transactionBody, record);
    }

    private void assertFailedFileTransaction(TransactionBody transactionBody, TransactionRecord record) {
        com.hedera.mirror.common.domain.transaction.Transaction transaction =
                getDbTransaction(record.getConsensusTimestamp());
        assertAll(() -> assertTransactionAndRecord(transactionBody, record), () -> assertThat(transaction.getEntityId())
                .isEqualTo(EntityId.of(record.getReceipt().getFileID())));
    }

    private void assertFileEntity(FileCreateTransactionBody expected, Timestamp consensusTimestamp) {
        Entity actualFile = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertEquals(
                        DomainUtils.timeStampInNanos(expected.getExpirationTime()),
                        actualFile.getExpirationTimestamp()),
                () -> assertEquals(expected.getMemo(), actualFile.getMemo()),
                () -> assertArrayEquals(expected.getKeys().toByteArray(), actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    private void assertFileEntityAndData(FileCreateTransactionBody expected, Timestamp consensusTimestamp) {
        assertFileEntity(expected, consensusTimestamp);
        assertFileData(expected.getContents(), consensusTimestamp);
    }

    private void assertFileEntity(FileUpdateTransactionBody expected, Timestamp consensusTimestamp) {
        Entity actualFile = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertEquals(
                        DomainUtils.timeStampInNanos(expected.getExpirationTime()),
                        actualFile.getExpirationTimestamp()),
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertEquals(expected.getMemo().getValue(), actualFile.getMemo()),
                () -> assertArrayEquals(expected.getKeys().toByteArray(), actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    private void assertFileEntityAndData(FileUpdateTransactionBody expected, Timestamp consensusTimestamp) {
        assertFileEntity(expected, consensusTimestamp);
        assertFileData(expected.getContents(), consensusTimestamp);
    }

    private void assertFileData(ByteString expected, Timestamp consensusTimestamp) {
        FileData actualFileData = fileDataRepository
                .findById(DomainUtils.timeStampInNanos(consensusTimestamp))
                .get();
        assertArrayEquals(expected.toByteArray(), actualFileData.getFileData());
    }

    private void assertAddressBookData(byte[] expected, Timestamp consensusTimestamp) {
        // addressBook.getStartConsensusTimestamp = transaction.consensusTimestamp + 1ns
        AddressBook actualAddressBook = addressBookRepository
                .findById(DomainUtils.timeStampInNanos(consensusTimestamp) + 1)
                .get();
        assertArrayEquals(expected, actualAddressBook.getFileData());
    }

    private void assertFileEntityHasNullFields(Timestamp consensusTimestamp) {
        Entity actualFile = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertNull(actualFile.getKey()),
                () -> assertNull(actualFile.getExpirationTimestamp()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    private void assertRowCountOnSuccess(FileID fileID) {
        assertRowCount(
                1,
                3, // 3 fee transfers
                1,
                EntityId.of(fileID));
    }

    private void assertRowCountOnTwoFileTransactions() {
        assertRowCount(
                2,
                6, // 3 + 3 fee transfers
                2,
                EntityId.of(FILE_ID));
    }

    private void assertRowCountOnSuccessNoData(FileID fileID) {
        assertRowCount(
                1,
                3, // 3 fee transfers
                0,
                EntityId.of(fileID));
    }

    private void assertRowCountOnFailureNoData() {
        assertRowCount(
                1, 3, // 3 fee transfers
                0);
    }

    private void assertRowCountOnAddressBookTransactions() {
        assertRowCount(
                2,
                6, // 3 + 3 fee transfers
                2,
                EntityId.of(ADDRESS_BOOK_FILEID));
    }

    private void assertRowCount(int numTransactions, int numCryptoTransfers, int numFileData, EntityId... entityIds) {
        assertAll(
                () -> assertEquals(numTransactions, transactionRepository.count()),
                () -> assertEntities(entityIds),
                () -> assertEquals(numCryptoTransfers, cryptoTransferRepository.count()),
                () -> assertEquals(numFileData, fileDataRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()));
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody) {
        return transactionRecord(transactionBody, FILE_ID);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, ResponseCodeEnum status) {
        return transactionRecord(transactionBody, status, FILE_ID);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, FileID fileId) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS, fileId);
    }

    private TransactionRecord transactionRecord(
            TransactionBody transactionBody, ResponseCodeEnum status, FileID fileId) {
        return buildTransactionRecord(
                recordBuilder -> recordBuilder.getReceiptBuilder().setFileID(fileId),
                transactionBody,
                status.getNumber());
    }

    private Transaction fileCreateTransaction() {
        return fileCreateTransaction(Timestamp.newBuilder()
                .setSeconds(1571487857L)
                .setNanos(181579000)
                .build());
    }

    private Transaction fileCreateTransaction(Timestamp expirationTime) {
        return buildTransaction(builder -> builder.getFileCreateBuilder()
                .setContents(ByteString.copyFromUtf8("Hedera hashgraph is great!"))
                .setExpirationTime(expirationTime)
                .setMemo("FileCreate memo")
                .setNewRealmAdminKey(keyFromString(KEY2))
                .setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build())
                .setShardID(ShardID.newBuilder().setShardNum(0))
                .getKeysBuilder()
                .addKeys(keyFromString(KEY)));
    }

    private Transaction fileAppendTransaction() {
        return fileAppendTransaction(FILE_ID, FILE_CONTENTS);
    }

    private Transaction fileAppendTransaction(FileID fileToAppendTo, byte[] contents) {
        return buildTransaction(builder -> builder.getFileAppendBuilder()
                .setContents(ByteString.copyFrom(contents))
                .setFileID(fileToAppendTo));
    }

    private Transaction fileUpdateAllTransaction() {
        return fileUpdateAllTransaction(FILE_ID, FILE_CONTENTS);
    }

    private Transaction fileUpdateAllTransaction(FileID fileToUpdate, byte[] contents) {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setContents(ByteString.copyFrom(contents))
                .setFileID(fileToUpdate)
                .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                .setMemo(StringValue.of("FileUpdate memo"))
                .getKeysBuilder()
                .addKeys(keyFromString(KEY)));
    }

    private Transaction fileUpdateContentsTransaction() {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setContents(ByteString.copyFromUtf8("Hedera hashgraph is even better!"))
                .setFileID(FILE_ID));
    }

    private Transaction fileUpdateExpiryTransaction() {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setFileID(FILE_ID)
                .setExpirationTime(Utility.instantToTimestamp(Instant.now())));
    }

    private Transaction fileUpdateKeysTransaction() {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setFileID(FILE_ID)
                .getKeysBuilder()
                .addKeys(keyFromString(KEY)));
    }

    private Transaction fileDeleteTransaction() {
        return buildTransaction(builder -> builder.getFileDeleteBuilder().setFileID(FILE_ID));
    }

    private Transaction systemDeleteTransaction() {
        return buildTransaction(builder -> builder.getSystemDeleteBuilder()
                .setFileID(FILE_ID)
                .setExpirationTime(
                        TimestampSeconds.newBuilder().setSeconds(100000).build()));
    }

    private Transaction systemUnDeleteTransaction() {
        return buildTransaction(builder -> builder.getSystemUndeleteBuilder().setFileID(FILE_ID));
    }
}
