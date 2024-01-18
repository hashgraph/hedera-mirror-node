/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class FileUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private AddressBookService addressBookService;

    @Override
    protected TransactionHandler getTransactionHandler() {
        var fileDataHandler = new FileDataHandler(addressBookService, entityListener, entityProperties);
        return new FileUpdateTransactionHandler(entityIdService, entityListener, fileDataHandler);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setFileUpdate(FileUpdateTransactionBody.newBuilder()
                        .setFileID(FileID.newBuilder()
                                .setFileNum(DEFAULT_ENTITY_NUM)
                                .build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.FILE;
    }

    @Test
    void updateTransaction() {
        // Given
        var recordItem = recordItemBuilder.fileUpdate().build();
        var transaction = domainBuilder.transaction().get();
        var fileData = ArgumentCaptor.forClass(FileData.class);
        var transactionBody = recordItem.getTransactionBody().getFileUpdate();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onFileData(fileData.capture());
        assertThat(fileData.getValue())
                .returns(transaction.getConsensusTimestamp(), FileData::getConsensusTimestamp)
                .returns(transaction.getEntityId(), FileData::getEntityId)
                .returns(transactionBody.getContents().toByteArray(), FileData::getFileData)
                .returns(transaction.getType(), FileData::getTransactionType);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setFiles(false);
        entityProperties.getPersist().setSystemFiles(false);
        var recordItem = recordItemBuilder.fileUpdate().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(addressBookService).isAddressBook(transaction.getEntityId());
        verifyNoMoreInteractions(addressBookService);
        verify(entityListener, never()).onFileData(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionPersistFilesFalse() {
        // Given
        var systemFileId = EntityId.of(0, 0, 120);
        entityProperties.getPersist().setFiles(false);
        entityProperties.getPersist().setSystemFiles(true);
        var recordItem = recordItemBuilder.fileUpdate().build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.entityId(systemFileId))
                .get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(addressBookService).isAddressBook(systemFileId);
        verifyNoMoreInteractions(addressBookService);
        verify(entityListener).onFileData(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionPersistSystemFilesFalse() {
        // Given
        var fileId = EntityId.of(0, 0, 1001);
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(false);
        var recordItem = recordItemBuilder.fileUpdate().build();
        var transaction =
                domainBuilder.transaction().customize(t -> t.entityId(fileId)).get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(addressBookService).isAddressBook(fileId);
        verifyNoMoreInteractions(addressBookService);
        verify(entityListener).onFileData(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionAddressBook() {
        // Given
        var systemFileId = EntityId.of(0, 0, 102);
        var recordItem = recordItemBuilder.fileUpdate().build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.entityId(systemFileId))
                .get();
        doReturn(true).when(addressBookService).isAddressBook(systemFileId);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(addressBookService).isAddressBook(systemFileId);
        verify(addressBookService).update(any());
        verify(entityListener, never()).onFileData(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
