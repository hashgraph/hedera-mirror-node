package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

class FileCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new FileCreateTransactionHandler(entityIdService, entityListener, new RecordParserProperties());
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setFileCreate(FileCreateTransactionBody.getDefaultInstance());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum)
                .setFileID(FileID.newBuilder().setFileNum(DEFAULT_ENTITY_NUM).build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.FILE;
    }
}
