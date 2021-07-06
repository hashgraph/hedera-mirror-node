package com.hedera.mirror.importer.parser.record.transactionhandler;

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

import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Arrays;

import com.hedera.mirror.importer.domain.EntityTypeEnum;

class FileCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final KeyList KEY_LIST = KeyList.newBuilder()
            .addAllKeys(Arrays.asList(DEFAULT_KEY, DEFAULT_SUBMIT_KEY))
            .build();

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new FileCreateTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setFileCreate(FileCreateTransactionBody.getDefaultInstance());
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return super.getDefaultTransactionRecord()
                .setReceipt(TransactionReceipt.newBuilder()
                        .setFileID(FileID.newBuilder().setFileNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return EntityTypeEnum.FILE;
    }
}
