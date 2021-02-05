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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import com.hedera.mirror.importer.domain.EntityTypeEnum;

public class ScheduleCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ScheduleCreateTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setScheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .setAdminKey(Key.newBuilder()
                                .setEd25519(ByteString
                                        .copyFromUtf8(
                                                "4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f"))
                                .build())
                        .setPayerAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1)
                                .build())
                        .setSigMap(SignatureMap.newBuilder()
                                .addSigPair(SignaturePair.newBuilder()
                                        .setEd25519(ByteString.copyFromUtf8("Ed25519-1"))
                                        .setPubKeyPrefix(ByteString.copyFromUtf8("PubKeyPrefix-1")).build())
                                .addSigPair(SignaturePair.newBuilder()
                                        .setEd25519(ByteString.copyFromUtf8("Ed25519-2"))
                                        .setPubKeyPrefix(ByteString.copyFromUtf8("PubKeyPrefix-2")).build())
                                .addSigPair(SignaturePair.newBuilder()
                                        .setEd25519(ByteString.copyFromUtf8("Ed25519-3"))
                                        .setPubKeyPrefix(ByteString.copyFromUtf8("PubKeyPrefix-3")).build())
                                .build())
                        .setTransactionBody(ByteString.copyFromUtf8("transaction body"))
                        .setMemo("schedule memo"));
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return TransactionRecord.newBuilder()
                .setReceipt(TransactionReceipt.newBuilder()
                        .setScheduleID(ScheduleID.newBuilder().setScheduleNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return EntityTypeEnum.SCHEDULE;
    }
}
