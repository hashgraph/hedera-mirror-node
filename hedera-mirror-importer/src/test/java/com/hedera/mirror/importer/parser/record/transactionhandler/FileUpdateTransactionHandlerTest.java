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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.Arrays;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.util.Utility;

class FileUpdateTransactionHandlerTest extends AbstractUpdatesEntityTransactionHandlerTest {

    private static final Timestamp EXPIRATION_TIME = Utility.instantToTimestamp(Instant.now());

    private final KeyList KEY_LIST = KeyList.newBuilder().addAllKeys(
            Arrays.asList(
                    getKey("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f"),
                    getKey("5b5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96g")
            ))
            .build();

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new FileUpdateTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setFileUpdate(FileUpdateTransactionBody.newBuilder()
                        .setFileID(FileID.newBuilder().setFileNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return EntityTypeEnum.FILE;
    }

    @Override
    ByteString getUpdateEntityTransactionBody() {
        return TransactionBody.newBuilder().setFileUpdate(
                FileUpdateTransactionBody.newBuilder()
                        .setExpirationTime(EXPIRATION_TIME)
                        .setKeys(KEY_LIST)
                        .build())
                .build().toByteString();
    }

    @Override
    void buildUpdateEntityExpectedEntity(Entities entity) {
        entity.setExpiryTimeNs(Utility.timestampInNanosMax(EXPIRATION_TIME));
        entity.setKey(KEY_LIST.toByteArray());
    }
}
