package com.hedera.mirror.importer.parser.record.entity;

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
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
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
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.NetworkAddressBook;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

public class EntityRecordItemListenerFileTest extends AbstractEntityRecordItemListenerTest {
    private static final FileID fileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(1001).build();
    private static final byte[] FILE_CONTENTS = {'a', 'b', 'c'};

    @TempDir
    Path dataPath;

    @Resource
    private MirrorProperties mirrorProperties;

    @Resource
    private NetworkAddressBook networkAddressBook;

    @Value("classpath:addressbook/mainnet")
    private File addressBookLarge;

    @Value("classpath:addressbook/testnet")
    private File addressBookSmall;

    @BeforeEach
    void before() throws Exception {
        mirrorProperties.setDataPath(dataPath);
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        parserProperties.init();
    }

    @Test
    void fileCreate() throws Exception {
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertRowCountOnSuccess(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntityAndData(transactionBody.getFileCreate(), record.getConsensusTimestamp())
        );
    }

    @Test
    void fileCreateDoNotPersist() throws Exception {
        entityProperties.getPersist().setFiles(false);
        entityProperties.getPersist().setSystemFiles(false);
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertRowCountOnSuccessNoData(),
                () -> assertFileTransaction(transactionBody, record, false)
        );
    }

    @Test
    void fileCreatePersistSystemPositive() throws Exception {
        entityProperties.getPersist().setFiles(false);
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody,
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(10).build());

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertRowCountOnSuccess(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntityAndData(transactionBody.getFileCreate(), record.getConsensusTimestamp())
        );
    }

    @Test
    void fileCreatePersistSystemNegative() throws Exception {
        entityProperties.getPersist().setFiles(false);
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody,
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(2000).build());

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertRowCountOnSuccessNoData(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntity(transactionBody.getFileCreate(), record.getConsensusTimestamp())
        );
    }

    @ParameterizedTest(name = "with {0} s and expected {1} ns")
    @CsvSource({
            "9223372036854775807, 9223372036854775807",
            "31556889864403199, 9223372036854775807",
            "-9223372036854775808, -9223372036854775808",
            "-1000000000000000000, -9223372036854775808"
    })
    void fileCreateExpirationTimeOverflow(long seconds, long expectedNanosTimestamp) throws Exception {
        Transaction transaction = fileCreateTransaction(Timestamp.newBuilder().setSeconds(seconds).build());
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());
        assertEquals(expectedNanosTimestamp, dbAccountEntity.getExpiryTimeNs());
    }

    @Test
    void fileAppendToExisting() throws Exception {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(fileCreateTransaction, recordCreate));

        // now append
        Transaction transaction = fileAppendTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities actualFile = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnTwoFileTransactions()
                , () -> assertFileTransaction(transactionBody, record, false)
                , () -> assertFileData(transactionBody.getFileAppend().getContents(), record.getConsensusTimestamp())
                // Additional entity checks
                , () -> assertNotNull(actualFile.getExpiryTimeNs())
                , () -> assertNull(actualFile.getAutoRenewPeriod())
                , () -> assertNull(actualFile.getProxyAccountId())
        );
    }

    @Test
    void fileAppendToNew() throws Exception {
        Transaction transaction = fileAppendTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbFileEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnSuccess()
                , () -> assertFileTransaction(transactionBody, record, false)
                , () -> assertFileData(transactionBody.getFileAppend().getContents(), record.getConsensusTimestamp())
                // Additional entity checks
                , () -> assertNull(dbFileEntity.getExpiryTimeNs())
                , () -> assertNull(dbFileEntity.getAutoRenewPeriod())
                , () -> assertNull(dbFileEntity.getProxyAccountId())
        );
    }

    @Test
    void fileAppendToSystemFile() throws Exception {
        Transaction transaction = fileAppendTransaction(
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(10).build(), FILE_CONTENTS);
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody,
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(10).build());

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertRowCountOnSuccess(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntityHasNullFields(record.getConsensusTimestamp()),
                () -> assertFileData(transactionBody.getFileAppend().getContents(), record.getConsensusTimestamp())
        );
    }

    @Test
    void fileUpdateAllToExisting() throws Exception {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(fileCreateTransaction, recordCreate));

        // now update
        Transaction transaction = fileUpdateAllTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertRowCountOnTwoFileTransactions(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntityAndData(transactionBody.getFileUpdate(), record.getConsensusTimestamp())
        );
    }

    @Test
    void fileUpdateAllToExistingFailedTransaction() throws Exception {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(fileCreateTransaction, recordCreate));

        // now update
        Transaction transaction = fileUpdateAllTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(4, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(1, fileDataRepository.count())
                , () -> assertFileTransaction(transactionBody, record, false)
                , () -> assertFileEntity(createTransactionBody.getFileCreate(), record.getConsensusTimestamp())
        );
    }

    @Test
    void fileAppendToAddressBook() throws Exception {
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        byte[] addressBook = FileUtils.readFileToByteArray(addressBookLarge);
        byte[] addressBookUpdate = Arrays.copyOf(addressBook, 6144);
        byte[] addressBookAppend = Arrays.copyOfRange(addressBook, 6144, addressBook.length);
        FileID fileID = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(102).build();

        // Initial address book update
        Transaction transactionUpdate = fileUpdateAllTransaction(fileID, addressBookUpdate);
        TransactionBody transactionBodyUpdate = TransactionBody.parseFrom(transactionUpdate.getBodyBytes());
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBodyUpdate.getFileUpdate();
        TransactionRecord recordUpdate = transactionRecord(transactionBodyUpdate, fileID);

        // Address book append
        Transaction transactionAppend = fileAppendTransaction(fileID, addressBookAppend);
        TransactionBody transactionBodyAppend = TransactionBody.parseFrom(transactionAppend.getBodyBytes());
        FileAppendTransactionBody fileAppendTransactionBody = transactionBodyAppend.getFileAppend();
        TransactionRecord recordAppend = transactionRecord(transactionBodyAppend, fileID);

        parseRecordItemAndCommit(new RecordItem(transactionUpdate, recordUpdate));
        parseRecordItemAndCommit(new RecordItem(transactionAppend, recordAppend));

        assertThat(networkAddressBook.getAddresses())
                .describedAs("Should overwrite address book with complete update")
                .hasSize(13);

        assertAll(
                () -> assertRowCountOnTwoFileTransactions()
                , () -> assertFileTransaction(transactionBodyUpdate, recordUpdate, false)
                , () -> assertFileTransaction(transactionBodyAppend, recordAppend, false)
                , () -> assertFileData(fileAppendTransactionBody.getContents(), recordAppend.getConsensusTimestamp())
                , () -> assertFileData(fileUpdateTransactionBody.getContents(), recordUpdate.getConsensusTimestamp())
                , () -> assertAddressBookData(addressBook, true)
                , () -> assertEquals(13, nodeAddressRepository.count())
                , () -> assertEquals(2, addressBookRepository.count()) // update and append
        );
    }

    @Test
    void fileUpdateAllToNew() throws Exception {
        Transaction transaction = fileUpdateAllTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertRowCountOnSuccess(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntityAndData(transactionBody.getFileUpdate(), record.getConsensusTimestamp())
        );
    }

    @Test
    void fileUpdateContentsToExisting() throws Exception {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(fileCreateTransaction, recordCreate));

        // now update
        Transaction transaction = fileUpdateContentsTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities actualFile = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnTwoFileTransactions()
                , () -> assertFileTransaction(transactionBody, record, false)
                , () -> assertFileData(transactionBody.getFileUpdate().getContents(), record.getConsensusTimestamp())
                // Additional entity checks
                , () -> assertNotNull(actualFile.getExpiryTimeNs())
                , () -> assertNotNull(actualFile.getKey())
                , () -> assertNull(actualFile.getAutoRenewPeriod())
                , () -> assertNull(actualFile.getProxyAccountId())
        );
    }

    @Test
    void fileUpdateContentsToNew() throws Exception {
        Transaction transaction = fileUpdateContentsTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities actualFile = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnSuccess()
                , () -> assertFileTransaction(transactionBody, record, false)
                , () -> assertFileData(transactionBody.getFileUpdate().getContents(), record.getConsensusTimestamp())
                // Additional entity checks
                , () -> assertNull(actualFile.getKey())
                , () -> assertNull(actualFile.getAutoRenewPeriod())
                , () -> assertNull(actualFile.getProxyAccountId())
        );
    }

    @Test
    void fileUpdateExpiryToExisting() throws Exception {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(fileCreateTransaction, recordCreate));

        // now update
        Transaction transaction = fileUpdateExpiryTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities actualFile = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                //TODO: Review row count of fileDataRepository with issue #294, probably should be 1
                () -> assertRowCountOnTwoFileTransactions()
                , () -> assertFileTransaction(transactionBody, record, false)
                // Additional entity checks
                , () -> assertEquals(Utility.timeStampInNanos(transactionBody.getFileUpdate().getExpirationTime()),
                        actualFile.getExpiryTimeNs())
                , () -> assertNotNull(actualFile.getKey())
                , () -> assertNull(actualFile.getAutoRenewPeriod())
                , () -> assertNull(actualFile.getProxyAccountId())
        );
    }

    @Test
    void fileUpdateExpiryToNew() throws Exception {
        Transaction transaction = fileUpdateExpiryTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities actualFile = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                //TODO: Review row count in fileDataRepository with issue #294, probably should be 0
                () -> assertRowCountOnSuccess()
                , () -> assertFileTransaction(transactionBody, record, false)
                // Additional entity checks
                , () -> assertEquals(Utility.timeStampInNanos(transactionBody.getFileUpdate().getExpirationTime()),
                        actualFile.getExpiryTimeNs())
                , () -> assertNull(actualFile.getKey())
                , () -> assertNull(actualFile.getAutoRenewPeriod())
                , () -> assertNull(actualFile.getProxyAccountId())
        );
    }

    @Test
    void fileUpdateKeysToExisting() throws Exception {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(fileCreateTransaction, recordCreate));

        // now update
        Transaction transaction = fileUpdateKeysTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbFileEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                //TODO: Review row count of fileDataRepository with issue #294, probably should be 1
                () -> assertRowCountOnTwoFileTransactions()
                , () -> assertFileTransaction(transactionBody, record, false)
                // Additional entity checks
                , () -> assertNotNull(dbFileEntity.getExpiryTimeNs())
                , () -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.getKey())
                , () -> assertNull(dbFileEntity.getAutoRenewPeriod())
                , () -> assertNull(dbFileEntity.getProxyAccountId())
        );
    }

    @Test
    void fileUpdateKeysToNew() throws Exception {
        Transaction transaction = fileUpdateKeysTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbFileEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                //TODO: Review row count in fileDataRepository with issue #294, probably should be 0
                () -> assertRowCountOnSuccess()
                , () -> assertFileTransaction(transactionBody, record, false)
                // Additional entity checks
                , () -> assertNull(dbFileEntity.getExpiryTimeNs())
                , () -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.getKey())
                , () -> assertNull(dbFileEntity.getAutoRenewPeriod())
                , () -> assertNull(dbFileEntity.getProxyAccountId())
        );
    }

    @Test
    void fileUpdateAllToNewSystem() throws Exception {
        Transaction transaction = fileUpdateAllTransaction(
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(10).build(), FILE_CONTENTS);
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord record = transactionRecord(transactionBody,
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(10).build());

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertRowCountOnSuccess(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntityAndData(fileUpdateTransactionBody, record.getConsensusTimestamp())
        );
    }

    @Test
    void fileUpdateAddressBookPartial() throws Exception {
        byte[] addressBookPrevious = FileUtils.readFileToByteArray(addressBookLarge);
        networkAddressBook.updateFrom(
                Instant.now().getEpochSecond(),
                addressBookPrevious,
                FileID.newBuilder().setFileNum(102).build(),
                false);
        byte[] addressBook = FileUtils.readFileToByteArray(addressBookSmall);
        byte[] addressBookUpdate = Arrays.copyOf(addressBook, 6144);
        Transaction transaction = fileUpdateAllTransaction(
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(102).build(), addressBookUpdate);
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord record = transactionRecord(transactionBody,
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(102).build());

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertThat(networkAddressBook.getAddresses())
                .describedAs("Should not overwrite address book with partial update")
                .hasSize(13);

        assertAll(
                () -> assertRowCountOnSuccess(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntityAndData(fileUpdateTransactionBody, record.getConsensusTimestamp()),
                () -> assertAddressBookData(addressBookUpdate, false)
                , () -> assertEquals(13, nodeAddressRepository.count())
                , () -> assertEquals(2, addressBookRepository.count()) // initial and 2 updates
        );
    }

    @Test
    void fileUpdateAddressBookComplete() throws Exception {
        byte[] addressBook = FileUtils.readFileToByteArray(addressBookSmall);
        assertThat(addressBook).hasSizeLessThan(6144);
        Transaction transaction = fileUpdateAllTransaction(
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(102).build(), addressBook);
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord record = transactionRecord(transactionBody,
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(102).build());

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertThat(networkAddressBook.getAddresses())
                .describedAs("Should overwrite address book with complete update")
                .hasSize(4);
        assertAll(
                () -> assertRowCountOnSuccess(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntityAndData(fileUpdateTransactionBody, record.getConsensusTimestamp()),
                () -> assertAddressBookData(addressBook, true)
                , () -> assertEquals(4, nodeAddressRepository.count())
                , () -> assertEquals(1, addressBookRepository.count()) // initial and new
        );
    }

    @Test
    void fileDeleteToExisting() throws Exception {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(fileCreateTransaction, recordCreate));

        // now update
        Transaction transaction = fileDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbFileEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(4, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(1, fileDataRepository.count())
                , () -> assertFileTransaction(transactionBody, record, true)
                // Additional entity checks
                , () -> assertNotNull(dbFileEntity.getKey())
                , () -> assertNotNull(dbFileEntity.getExpiryTimeNs())
                , () -> assertNull(dbFileEntity.getAutoRenewPeriod())
                , () -> assertNull(dbFileEntity.getProxyAccountId())
        );
    }

    @Test
    void fileDeleteToNew() throws Exception {
        Transaction fileDeleteTransaction = fileDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(fileDeleteTransaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(fileDeleteTransaction, record));

        assertAll(
                () -> assertRowCountOnSuccessNoData(),
                () -> assertFileTransaction(transactionBody, record, true),
                () -> assertFileEntityHasNullFields(record.getConsensusTimestamp())
        );
    }

    @Test
    void fileDeleteFailedTransaction() throws Exception {
        Transaction fileDeleteTransaction = fileDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(fileDeleteTransaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);

        parseRecordItemAndCommit(new RecordItem(fileDeleteTransaction, record));

        assertAll(
                () -> assertRowCountOnSuccessNoData(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntityHasNullFields(record.getConsensusTimestamp())
        );
    }

    @Test
    void fileSystemDeleteTransaction() throws Exception {
        Transaction systemDeleteTransaction = systemDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(systemDeleteTransaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(systemDeleteTransaction, record));

        assertAll(
                () -> assertRowCountOnSuccessNoData(),
                () -> assertFileTransaction(transactionBody, record, true),
                () -> assertFileEntityHasNullFields(record.getConsensusTimestamp())
        );
    }

    @Test
    void fileSystemUnDeleteTransaction() throws Exception {
        Transaction systemUndeleteTransaction = systemUnDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(systemUndeleteTransaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(systemUndeleteTransaction, record));

        assertAll(
                () -> assertRowCountOnSuccessNoData(),
                () -> assertFileTransaction(transactionBody, record, false),
                () -> assertFileEntityHasNullFields(record.getConsensusTimestamp())
        );
    }

    @Test
    void fileSystemDeleteInvalidTransaction() throws Exception {
        Transaction systemDeleteTransaction = systemDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(systemDeleteTransaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(systemDeleteTransaction, record));

        assertAll(
                () -> assertRowCountOnSuccessNoData(),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    @Test
    void fileSystemUnDeleteFailedTransaction() throws Exception {
        Transaction systemUndeleteTransaction = systemUnDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(systemUndeleteTransaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(systemUndeleteTransaction, record));

        assertAll(
                () -> assertRowCountOnSuccessNoData(),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    private void assertFileTransaction(TransactionBody transactionBody, TransactionRecord record, boolean deleted) {
        Entities actualFile = getTransactionEntity(record.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertFile(record.getReceipt().getFileID(), actualFile),
                () -> assertEquals(deleted, actualFile.isDeleted()));
    }

    private void assertFileEntity(FileCreateTransactionBody expected, Timestamp consensusTimestamp) {
        Entities actualFile = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertEquals(Utility.timeStampInNanos(expected.getExpirationTime()), actualFile
                        .getExpiryTimeNs()),
                () -> assertArrayEquals(expected.getKeys().toByteArray(), actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId())
        );
    }

    private void assertFileEntityAndData(FileCreateTransactionBody expected, Timestamp consensusTimestamp) {
        assertFileEntity(expected, consensusTimestamp);
        assertFileData(expected.getContents(), consensusTimestamp);
    }

    private void assertFileEntity(FileUpdateTransactionBody expected, Timestamp consensusTimestamp) {
        Entities actualFile = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertEquals(Utility.timeStampInNanos(expected.getExpirationTime()), actualFile
                        .getExpiryTimeNs()),
                () -> assertArrayEquals(expected.getKeys().toByteArray(), actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId())
        );
    }

    private void assertFileEntityAndData(FileUpdateTransactionBody expected, Timestamp consensusTimestamp) {
        assertFileEntity(expected, consensusTimestamp);
        assertFileData(expected.getContents(), consensusTimestamp);
    }

    private void assertFileData(ByteString expected, Timestamp consensusTimestamp) {
        FileData actualFileData = fileDataRepository.findById(Utility.timeStampInNanos(consensusTimestamp)).get();
        assertArrayEquals(expected.toByteArray(), actualFileData.getFileData());
    }

    private void assertAddressBookData(byte[] expected, boolean complete) {
        AddressBook actualAddressBook = complete ? networkAddressBook.getCurrentAddressBook() :
                networkAddressBook.getPartialAddressBook();
        assertArrayEquals(expected, actualAddressBook.getFileData());
    }

    private void assertFileEntityHasNullFields(Timestamp consensusTimestamp) {
        Entities actualFile = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertNull(actualFile.getKey()),
                () -> assertNull(actualFile.getExpiryTimeNs()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    private void assertRowCountOnSuccess() {
        assertRowCount(1,
                4, // accounts: node, payer, treasury, file
                3, // 3 fee transfers
                1);
    }

    private void assertRowCountOnTwoFileTransactions() {
        assertRowCount(2,
                4, // accounts: node, payer, treasury, file
                6, // 3 + 3 fee transfers
                2);
    }

    private void assertRowCountOnSuccessNoData() {
        assertRowCount(1,
                4, // accounts: node, payer, treasury, file
                3, // 3 fee transfers
                0);
    }

    private void assertRowCount(int numTransactions, int numEntities, int numCryptoTransfers, int numFileData) {
        assertAll(
                () -> assertEquals(numTransactions, transactionRepository.count()),
                () -> assertEquals(numEntities, entityRepository.count()),
                () -> assertEquals(numCryptoTransfers, cryptoTransferRepository.count()),
                () -> assertEquals(numFileData, fileDataRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, contractResultRepository.count())
        );
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody) {
        return transactionRecord(transactionBody, fileId);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, ResponseCodeEnum status) {
        return transactionRecord(transactionBody, status, fileId);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, FileID fileId) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS, fileId);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, ResponseCodeEnum status,
                                                FileID fileId) {
        return buildTransactionRecord(recordBuilder -> recordBuilder.getReceiptBuilder().setFileID(fileId),
                transactionBody, status.getNumber());
    }

    private Transaction fileCreateTransaction() {
        return fileCreateTransaction(Timestamp.newBuilder().setSeconds(1571487857L).setNanos(181579000).build());
    }

    private Transaction fileCreateTransaction(Timestamp expirationTime) {
        return buildTransaction(builder -> builder.getFileCreateBuilder()
                .setContents(ByteString.copyFromUtf8("Hedera hashgraph is great!"))
                .setExpirationTime(expirationTime)
                .setNewRealmAdminKey(keyFromString(KEY2))
                .setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build())
                .setShardID(ShardID.newBuilder().setShardNum(0))
                .getKeysBuilder().addKeys(keyFromString(KEY)));
    }

    private Transaction fileAppendTransaction() {
        return fileAppendTransaction(fileId, FILE_CONTENTS);
    }

    private Transaction fileAppendTransaction(FileID fileToAppendTo, byte[] contents) {
        return buildTransaction(builder -> builder.getFileAppendBuilder()
                .setContents(ByteString.copyFrom(contents))
                .setFileID(fileToAppendTo));
    }

    private Transaction fileUpdateAllTransaction() {
        return fileUpdateAllTransaction(fileId, FILE_CONTENTS);
    }

    private Transaction fileUpdateAllTransaction(FileID fileToUpdate, byte[] contents) {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setContents(ByteString.copyFrom(contents))
                .setFileID(fileToUpdate)
                .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                .getKeysBuilder().addKeys(keyFromString(KEY)));
    }

    private Transaction fileUpdateContentsTransaction() {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setContents(ByteString.copyFromUtf8("Hedera hashgraph is even better!"))
                .setFileID(fileId));
    }

    private Transaction fileUpdateExpiryTransaction() {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setFileID(fileId)
                .setExpirationTime(Utility.instantToTimestamp(Instant.now())));
    }

    private Transaction fileUpdateKeysTransaction() {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setFileID(fileId)
                .getKeysBuilder().addKeys(keyFromString(KEY)));
    }

    private Transaction fileDeleteTransaction() {
        return buildTransaction(builder -> builder.getFileDeleteBuilder().setFileID(fileId));
    }

    private Transaction systemDeleteTransaction() {
        return buildTransaction(builder -> builder.getSystemDeleteBuilder()
                .setFileID(fileId)
                .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(100000).build()));
    }

    private Transaction systemUnDeleteTransaction() {
        return buildTransaction(builder -> builder.getSystemUndeleteBuilder().setFileID(fileId));
    }
}
