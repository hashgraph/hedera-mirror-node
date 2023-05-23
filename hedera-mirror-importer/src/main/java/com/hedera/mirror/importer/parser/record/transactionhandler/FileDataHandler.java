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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.util.DomainUtils.toBytes;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
final class FileDataHandler {

    private final AddressBookService addressBookService;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    void handle(Transaction transaction, ByteString contents) {
        var fileId = transaction.getEntityId();
        var fileData = new FileData();
        fileData.setConsensusTimestamp(transaction.getConsensusTimestamp());
        fileData.setEntityId(fileId);
        fileData.setFileData(toBytes(contents));
        fileData.setTransactionType(transaction.getType());

        // We always store file data for address books since they're used by the address book service
        if (addressBookService.isAddressBook(fileId)) {
            addressBookService.update(fileData);
        } else if (entityProperties.getPersist().isFiles()
                || (entityProperties.getPersist().isSystemFiles() && fileId.getEntityNum() < 1000)) {
            entityListener.onFileData(fileData);
        }
    }
}
